;; SPDX-License-Identifier: Apache-2.0
;; Copyright Contributors to the ODPi Egeria project.
;;
;; Based on original work licensed as follows:
;; The MIT License (MIT)
;;
;; Copyright © 2018-2021 JUXT LTD.
;;
;; Permission is hereby granted, free of charge, to any person obtaining a copy of
;; this software and associated documentation files (the "Software"), to deal in
;; the Software without restriction, including without limitation the rights to
;; use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
;; the Software, and to permit persons to whom the Software is furnished to do so,
;; subject to the following conditions:
;;
;; The above copyright notice and this permission notice shall be included in all
;; copies or substantial portions of the Software.
;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
;; FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
;; COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
;; IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
;; CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(ns egeria.crux.lucene
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [crux.bus :as bus]
            [crux.codec :as cc]
            [crux.db :as db]
            [crux.io :as cio]
            [crux.query :as q]
            [crux.system :as sys])
  (:import crux.query.VarBinding
           java.io.Closeable
           java.nio.file.Path
           org.apache.lucene.analysis.Analyzer
           [org.apache.lucene.analysis.core KeywordAnalyzer KeywordTokenizerFactory LowerCaseFilterFactory]
           [org.apache.lucene.analysis.custom CustomAnalyzer]
           [org.apache.lucene.document Document Field$Store StoredField StringField TextField]
           [org.apache.lucene.index DirectoryReader IndexWriter IndexWriterConfig Term]
           org.apache.lucene.queries.function.FunctionScoreQuery
           org.apache.lucene.queryparser.classic.QueryParser
           [org.apache.lucene.search BooleanClause$Occur BooleanQuery$Builder DoubleValuesSource IndexSearcher Query ScoreDoc TermQuery TopDocs]
           [org.apache.lucene.store Directory FSDirectory]))

(defrecord LuceneNode [directory analyzer kwanalyzer indexer]
  Closeable
  (close [this]
    (cio/try-close directory)))

(defn- ^String ->hash-str [eid]
  (str (cc/new-id eid)))

(defrecord DocumentId [a v])

(defn ^String keyword->k [k]
  (subs (str k) 1))

(defn ^String keyword->kcs [k]
  (subs (str k "-exact") 1))

(def ^:const ^:private field-crux-id "_crux_id")
(def ^:const ^:private field-crux-val "_crux_val")
(def ^:const ^:private field-crux-val-exact "_crux_val_exact")
(def ^:const ^:private field-crux-attr "_crux_attr")

(defn- ^IndexWriter index-writer [lucene-store]
  (let [{:keys [^Directory directory ^Analyzer analyzer]} lucene-store]
    (IndexWriter. directory, (IndexWriterConfig. analyzer))))

(defn- index-tx! [^IndexWriter index-writer tx]
  (let [t (Term. "meta" "latest-completed-tx")
        d (doto (Document.)
            (.add (StringField. "meta", "latest-completed-tx" Field$Store/NO))
            (.add (StoredField. "latest-completed-tx" ^long (:crux.tx/tx-id tx))))]
    (.updateDocument index-writer t d)))

(defn latest-submitted-tx [lucene-store]
  (let [{:keys [^Directory directory]} lucene-store]
    (when (DirectoryReader/indexExists directory)
      (with-open [directory-reader (DirectoryReader/open directory)]
        (let [index-searcher (IndexSearcher. directory-reader)
              q (TermQuery. (Term. "meta" "latest-completed-tx"))
              d ^ScoreDoc (first (.-scoreDocs (.search index-searcher q 1)))]
          (when d
            (some-> (.doc index-searcher (.-doc d))
                    (.get "latest-completed-tx")
                    (Long/parseLong))))))))

(defn validate-lucene-store-up-to-date [index-store lucene-store]
  (let [{:crux.tx/keys [tx-id] :as latest-tx} (db/latest-completed-tx index-store)
        latest-lucene-tx-id (latest-submitted-tx lucene-store)]
    (when (and tx-id
               (or (nil? latest-lucene-tx-id)
                   (> tx-id latest-lucene-tx-id)))
      (throw (IllegalStateException. "Lucene store latest tx mismatch")))))

(defn search [lucene-store, ^Query q]
  (assert lucene-store)
  (let [{:keys [^Directory directory]} lucene-store
        directory-reader (DirectoryReader/open directory)
        index-searcher (IndexSearcher. directory-reader)
        q (FunctionScoreQuery. q (DoubleValuesSource/fromQuery q))
        score-docs (letfn [(docs-page [after]
                             (lazy-seq
                              (let [^TopDocs
                                    top-docs (if after
                                               (.searchAfter index-searcher after q 100)
                                               (.search index-searcher q 100))
                                    score-docs (.-scoreDocs top-docs)]
                                (concat score-docs
                                        (when (= 100 (count score-docs))
                                          (docs-page (last score-docs)))))))]
                     (docs-page nil))]

    (when (seq score-docs)
      (log/debug (.explain index-searcher q (.-doc ^ScoreDoc (first score-docs)))))

    (cio/->cursor (fn []
                    (.close directory-reader))
                  (->> score-docs
                       (map (fn [^ScoreDoc d]
                              (vector (.doc index-searcher (.-doc d))
                                      (.-score d))))))))

(defn pred-constraint-ci [query-builder results-resolver {:keys [arg-bindings idx-id return-type tuple-idxs-in-join-order ::lucene-store]}]
  (fn pred-get-attr-constraint [index-snapshot db idx-id->idx join-keys]
    (let [arg-bindings (map (fn [a]
                              (if (instance? VarBinding a)
                                (q/bound-result-for-var index-snapshot a join-keys)
                                a))
                            (rest arg-bindings))
          query (query-builder (:analyzer lucene-store) arg-bindings)
          tuples (with-open [search-results ^crux.api.ICursor (search lucene-store query)]
                   (->> search-results
                        iterator-seq
                        (results-resolver index-snapshot db)
                        (into [])))]
      (q/bind-binding return-type tuple-idxs-in-join-order (get idx-id->idx idx-id) tuples))))

(defn pred-constraint-cs [query-builder results-resolver {:keys [arg-bindings idx-id return-type tuple-idxs-in-join-order ::lucene-store]}]
  (fn pred-get-attr-constraint [index-snapshot db idx-id->idx join-keys]
    (let [arg-bindings (map (fn [a]
                              (if (instance? VarBinding a)
                                (q/bound-result-for-var index-snapshot a join-keys)
                                a))
                            (rest arg-bindings))
          query (query-builder (:kwanalyzer lucene-store) arg-bindings)
          tuples (with-open [search-results ^crux.api.ICursor (search lucene-store query)]
                   (->> search-results
                        iterator-seq
                        (results-resolver index-snapshot db)
                        (into [])))]
      (q/bind-binding return-type tuple-idxs-in-join-order (get idx-id->idx idx-id) tuples))))

(defn resolve-search-results-a-v
  "Given search results each containing a single A/V pair document,
  perform a temporal resolution against A/V to resolve the eid."
  [attr index-snapshot {:keys [entity-resolver-fn] :as db} search-results]
  (mapcat (fn [[^Document doc score]]
            (let [v (.get ^Document doc field-crux-val)]
              (for [eid (doall (db/ave index-snapshot attr v nil entity-resolver-fn))]
                [(db/decode-value index-snapshot eid) v score])))
          search-results))

(defn ^Query build-query-ci
  "Standard build query fn (case-insensitive), taking a single field/val lucene term string."
  [^Analyzer analyzer, [k v]]
  (when-not (string? v)
    (throw (IllegalArgumentException. "Lucene text search values must be String")))
  (let [qp (doto (QueryParser. (keyword->k k) analyzer) (.setAllowLeadingWildcard true))
        b (doto (BooleanQuery$Builder.)
            (.add (.parse qp v) BooleanClause$Occur/MUST))]
    (.build b)))

(defmethod q/pred-args-spec 'text-search-ci [_]
  (s/cat :pred-fn  #{'text-search-ci} :args (s/spec (s/cat :attr keyword? :v (some-fn string? symbol?))) :return (s/? :crux.query/binding)))

(defmethod q/pred-constraint 'text-search-ci [_ pred-ctx]
  (let [resolver (partial resolve-search-results-a-v (second (:arg-bindings pred-ctx)))]
    (pred-constraint-ci #'build-query-ci resolver pred-ctx)))

(defn ^Query build-query-cs
  "Standard build query fn (case-sensitive), taking a single field/val lucene term string."
  [^Analyzer kwanalyzer, [k v]]
  (when-not (string? v)
    (throw (IllegalArgumentException. "Lucene text search values must be String")))
  (let [qp (doto (QueryParser. (keyword->kcs k) kwanalyzer) (.setAllowLeadingWildcard true))
        b (doto (BooleanQuery$Builder.)
                (.add (.parse qp v) BooleanClause$Occur/MUST))]
    (.build b)))

(defmethod q/pred-args-spec 'text-search-cs [_]
  (s/cat :pred-fn  #{'text-search-cs} :args (s/spec (s/cat :attr keyword? :v (some-fn string? symbol?))) :return (s/? :crux.query/binding)))

(defmethod q/pred-constraint 'text-search-cs [_ pred-ctx]
  (let [resolver (partial resolve-search-results-a-v (second (:arg-bindings pred-ctx)))]
    (pred-constraint-cs #'build-query-cs resolver pred-ctx)))

(defn- resolve-search-results-a-v-wildcard
  "Given search results each containing a single A/V pair document,
  perform a temporal resolution against A/V to resolve the eid."
  [index-snapshot {:keys [entity-resolver-fn] :as db} search-results]
  (mapcat (fn [[^Document doc score]]
            (let [v (.get ^Document doc field-crux-val)
                  a (keyword (.get ^Document doc field-crux-attr))]
              (for [eid (doall (db/ave index-snapshot a v nil entity-resolver-fn))]
                [(db/decode-value index-snapshot eid) v a score])))
          search-results))

(defn ^Query build-query-ci-wildcard
  "Wildcard query builder (case insensitive)"
  [^Analyzer analyzer, [v]]
  (when-not (string? v)
    (throw (IllegalArgumentException. "Lucene text search values must be String")))
  (let [qp (doto (QueryParser. field-crux-val analyzer) (.setAllowLeadingWildcard true))
        b (doto (BooleanQuery$Builder.)
            (.add (.parse qp v) BooleanClause$Occur/MUST))]
    (.build b)))

(defmethod q/pred-args-spec 'wildcard-text-search-ci [_]
  (s/cat :pred-fn #{'wildcard-text-search-ci} :args (s/spec (s/cat :v string?)) :return (s/? :crux.query/binding)))

(defmethod q/pred-constraint 'wildcard-text-search-ci [_ pred-ctx]
  (pred-constraint-ci #'build-query-ci-wildcard #'resolve-search-results-a-v-wildcard pred-ctx))

(defn ^Query build-query-cs-wildcard
  "Wildcard query builder (case-sensitive)"
  [^Analyzer kwanalyzer, [v]]
  (when-not (string? v)
    (throw (IllegalArgumentException. "Lucene text search values must be String")))
  (let [qp (doto (QueryParser. field-crux-val-exact kwanalyzer) (.setAllowLeadingWildcard true))
        b (doto (BooleanQuery$Builder.)
                (.add (.parse qp v) BooleanClause$Occur/MUST))]
    (.build b)))

(defmethod q/pred-args-spec 'wildcard-text-search-cs [_]
  (s/cat :pred-fn #{'wildcard-text-search-cs} :args (s/spec (s/cat :v string?)) :return (s/? :crux.query/binding)))

(defmethod q/pred-constraint 'wildcard-text-search-cs [_ pred-ctx]
  (pred-constraint-cs #'build-query-cs-wildcard #'resolve-search-results-a-v-wildcard pred-ctx))

(defprotocol LuceneIndexer
  (index! [this index-writer docs])
  (evict! [this index-writer eids]))

(defrecord LuceneAvIndexer [index-store]
  LuceneIndexer

  (index! [_ index-writer docs]
    (doseq [crux-doc (vals docs)
            [k v] (->> (dissoc crux-doc :crux.db/id)
                       (mapcat (fn [[k v]]
                                 (for [v (cc/vectorize-value v)
                                       :when (string? v)]
                                   [k v]))))
            :let [id-str (->hash-str (DocumentId. k v))
                  doc (doto (Document.)
                        ;; To search for triples by a-v for deduping
                        (.add (StringField. field-crux-id, id-str, Field$Store/NO))
                        ;; The actual term, which will be tokenized
                        (.add (TextField. (keyword->k k), v, Field$Store/YES))
                        ;; The actual term, to be used for exact matches
                        (.add (StringField. (keyword->kcs k), v, Field$Store/YES))
                        ;; Used for wildcard searches (case-insensitive)
                        (.add (TextField. field-crux-val, v, Field$Store/YES))
                        ;; Used for wildcard searches (case-sensitive)
                        (.add (StringField. field-crux-val-exact, v, Field$Store/YES))
                        ;; Used for wildcard searches
                        (.add (StringField. field-crux-attr, (keyword->k k), Field$Store/YES)))]]
      (.updateDocument ^IndexWriter index-writer (Term. field-crux-id id-str) doc)))

  (evict! [this index-writer eids]
    (let [attrs-id->attr (->> (db/read-index-meta index-store :crux/attribute-stats)
                              keys
                              (map #(vector (->hash-str %) %))
                              (into {}))]
      (with-open [index-snapshot (db/open-index-snapshot index-store)]
        (let [qs (for [[a v] (db/exclusive-avs index-store eids)
                       :let [a (attrs-id->attr (->hash-str a))
                             v (db/decode-value index-snapshot v)]
                       :when (not= :crux.db/id a)]
                   (TermQuery. (Term. field-crux-id (->hash-str (DocumentId. a v)))))]
          (.deleteDocuments ^IndexWriter index-writer ^"[Lorg.apache.lucene.search.Query;" (into-array Query qs)))))))

(defn ->indexer
  {::sys/deps {:index-store :crux/index-store}}
  [{:keys [index-store]}]
  (LuceneAvIndexer. index-store))

;; Used to index in a case-insensitive way
(defn ->analyzer
  [_]
  (.build (doto (CustomAnalyzer/builder)
            (.withTokenizer ^String KeywordTokenizerFactory/NAME ^"[Ljava.lang.String;" (into-array String []))
            (.addTokenFilter ^String LowerCaseFilterFactory/NAME ^"[Ljava.lang.String;" (into-array String [])))))

;; Used to still push-down case-sensitive queries
(defn ->kwanalyzer
  [_]
  (KeywordAnalyzer.))

(defn ->lucene-store
  {::sys/args {:db-dir {:doc "Lucene DB Dir"
                        :required? true
                        :spec ::sys/path}}
   ::sys/deps {:bus :crux/bus
               :document-store :crux/document-store
               :index-store :crux/index-store
               :query-engine :crux/query-engine
               :indexer `->indexer
               :analyzer `->analyzer
               :kwanalyzer `->kwanalyzer}
   ::sys/before #{[:crux/tx-ingester]}}
  [{:keys [^Path db-dir index-store document-store bus analyzer kwanalyzer indexer query-engine] :as opts}]
  (let [directory (FSDirectory/open db-dir)
        lucene-store (LuceneNode. directory analyzer kwanalyzer indexer)]
    ;; Ensure lucene index exists for immediate queries:
    (with-open [index-writer (index-writer lucene-store)]
      (.commit index-writer))
    (validate-lucene-store-up-to-date index-store lucene-store)
    (q/assoc-pred-ctx! query-engine ::lucene-store lucene-store)
    (bus/listen bus {:crux/event-types #{:crux.tx/committing-tx}
                     :crux.bus/executor (reify java.util.concurrent.Executor
                                          (execute [_ f]
                                            (.run f)))}
                (fn [ev]
                  (with-open [index-writer (index-writer lucene-store)]
                    (index! indexer index-writer (db/fetch-docs document-store (:doc-ids ev)))
                    (when-let [evicting-eids (not-empty (:evicting-eids ev))]
                      (evict! indexer index-writer evicting-eids))
                    (index-tx! index-writer (:submitted-tx ev)))))
    lucene-store))
