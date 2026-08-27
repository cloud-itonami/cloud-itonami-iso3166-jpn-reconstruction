;; Re-fetch every entry in facts.edn from the live authority.
;;
;;   nbb scripts/verify-facts.cljs
;;
;; ── THREE EXIT CODES, ON PURPOSE
;;
;;   0  every entry checked and every entry agreed with the register
;;   1  the register is wrong about the world -- a page is gone, a law id no
;;      longer resolves, a repeal happened. A claim, from a run that could
;;      make claims.
;;   2  REFUSED. This run could not answer. Not a pass.
;;
;; The third code is the point. "The check could not run" and "the check ran
;; and found nothing wrong" are the same shape in most verifiers, and a
;; register that silently degrades to unanimous agreement is worse than none,
;; because the green is indistinguishable from a green that was earned.
;;
;; So: no network, a host whose 404 stops discriminating, a needle that has
;; migrated into the site chrome, a statute pointed at the SPA host, a
;; self-test returning the wrong reason -- all 2, all printing REFUSED.
;;
;; ── WHY EACH CHECK IS THE CHECK IT IS
;;
;; Every non-obvious decision below is forced by something measured against
;; these hosts on 2026-08-27, and the reasons are written out in facts.edn's
;; header rather than repeated here. In short:
;;
;;   statutes are resolved at laws.e-gov.go.jp/api/2, never at
;;   elaws.e-gov.go.jp, because the latter answers 200 with an identical
;;   800-byte shell for a real law id and a fabricated one -- and it is the
;;   host the agency's own page links to;
;;
;;   repeal is read by comparing repeal_status to the literal strings, because
;;   a law in force carries the STRING "None", which is truthy, so both
;;   obvious spellings of the check fail silently and in opposite directions;
;;
;;   pages are followed through their redirects AND asserted to still redirect,
;;   because every deep link the agency publishes 301s while the section
;;   indexes do not, so neither following nor not-following is right on its own;
;;
;;   needles are re-tested against a live 404 body every run, because that body
;;   is 65 KB of site chrome and the obvious needles -- including the 調達情報
;;   page's own <h1> -- are on it.
;;
;; ── SELF-TESTS ASSERT THE REASON, NOT THE VERDICT
;;
;; A negative test that only asserts "this failed" counts a failure for the
;; wrong cause as a success. Every self-test below names the reason keyword it
;; expects, and the run REFUSES if a self-test fails for any other reason --
;; including passing.

(ns verify-facts
  (:require ["fs" :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;; ── fetching ───────────────────────────────────────────────────────────────

(defn- sleep [ms] (js/Promise. (fn [res] (js/setTimeout res ms))))

(defn- fetch-page
  "Follow redirects. Returns {:status :final-url :body :ctype} or {:error ...}.
   The redirect chain matters here, so :final-url is always reported."
  [url]
  (-> (js/fetch url #js {:redirect "follow"
                         :headers #js {"User-Agent" "cloud-itonami-iso3166-jpn-reconstruction facts verifier"}})
      (.then (fn [r] (.then (.text r)
                            (fn [t] {:status (.-status r)
                                     :final-url (.-url r)
                                     :ctype (or (.get (.-headers r) "content-type") "")
                                     :body t}))))
      (.catch (fn [e] {:error (str e)}))))

(defn- fetch-head
  "Do NOT follow. Returns {:status :location} -- the only way to see that a
   citation the agency still publishes is a redirect rather than the page.

   The Location this host sends is RELATIVE (/topics/...). Compared as-is
   against the absolute URL in the register it never matches, so every live
   redirect reports as a changed one -- a verifier that fails everything is
   as uninformative as one that passes everything. Resolved against the
   request URL, which is what a browser does and what the register records."
  [url]
  (-> (js/fetch url #js {:redirect "manual"
                         :headers #js {"User-Agent" "cloud-itonami-iso3166-jpn-reconstruction facts verifier"}})
      (.then (fn [r]
               (let [loc (.get (.-headers r) "location")]
                 {:status (.-status r)
                  :location (when loc (.-href (js/URL. loc url)))
                  :location-raw loc})))
      (.catch (fn [e] {:error (str e)}))))

(defn- fetch-json [url]
  (-> (js/fetch url #js {:headers #js {"User-Agent" "cloud-itonami-iso3166-jpn-reconstruction facts verifier"}})
      (.then (fn [r] (.then (.text r)
                            (fn [t] {:status (.-status r)
                                     :json (try (js->clj (js/JSON.parse t) :keywordize-keys true)
                                                (catch :default _ nil))}))))
      (.catch (fn [e] {:error (str e)}))))

;; ── html ───────────────────────────────────────────────────────────────────

(defn- page-title [body]
  (when-let [m (re-find #"(?is)<title[^>]*>(.*?)</title>" (or body ""))]
    (str/trim (str/replace (second m) #"\s+" " "))))

(defn- header-charset [ctype]
  (when-let [m (re-find #"(?i)charset=([\w-]+)" (or ctype ""))]
    (str/lower-case (second m))))

(defn- de-tag
  "Text with tags removed and whitespace collapsed. Needle tests run on this,
   not on raw HTML -- a needle that only matches inside an attribute or a
   script block is not the page saying it."
  [body]
  (-> (or body "")
      (str/replace #"(?is)<script.*?</script>" " ")
      (str/replace #"(?is)<style.*?</style>" " ")
      (str/replace #"<[^>]+>" " ")
      (str/replace #"\s+" " ")))

;; ── hosts ──────────────────────────────────────────────────────────────────
;;
;; Measured, not read off the register. Each returns either a measurement or
;; a :refuse -- because every later check depends on these being true, and a
;; host that has changed shape invalidates the checks written against it
;; rather than failing them.

(defn- measure-page-host
  "Prove this host's missing-page answer still discriminates, and capture the
   404 body so needles can be tested against it."
  [h]
  (let [probe (str (:host/missing-probe h) "zzz-no-such-page-9f3c2a1b8e/")]
    (-> (fetch-page probe)
        (.then
         (fn [miss]
           (-> (fetch-page (str "https://" (:host/name h) "/"))
               (.then
                (fn [live]
                  (cond
                    (:error miss)
                    {:refuse (str "could not reach " probe " — " (:error miss))}

                    (:error live)
                    {:refuse (str "could not reach the host root — " (:error live))}

                    ;; A fabricated path answering 200 means this host has
                    ;; started serving soft 404s, and every page check below
                    ;; -- all of which lean on status -- stops meaning
                    ;; anything. That is REFUSED, not a failure.
                    (not= (:status miss) (:host/missing-status h))
                    {:refuse (str "fabricated path " probe " answered "
                                  (:status miss) ", register says "
                                  (:host/missing-status h)
                                  ". Page checks on this host cannot be trusted.")}

                    (not= (page-title (:body miss)) (:host/missing-title h))
                    {:refuse (str "missing-page title is now "
                                  (pr-str (page-title (:body miss)))
                                  ", register says " (pr-str (:host/missing-title h)))}

                    ;; If the missing page and the front page were to answer
                    ;; alike, the 404 would carry no information at all.
                    (= (page-title (:body miss)) (page-title (:body live)))
                    {:refuse "missing page and front page now share a title; the 404 no longer discriminates"}

                    (and (:host/charset-in-header? h)
                         (not= (header-charset (:ctype miss)) (:host/charset h)))
                    {:refuse (str "charset in header is now "
                                  (pr-str (header-charset (:ctype miss)))
                                  ", register says " (pr-str (:host/charset h)))}

                    :else
                    {:missing-status (:status miss)
                     :missing-title (page-title (:body miss))
                     :missing-text (de-tag (:body miss))})))))))))

(defn- measure-indistinguishable-host
  "Prove the SPA host really cannot tell a real law id from a fabricated one.
   If it ever CAN, that is not good news -- it means facts.edn's header is out
   of date about the surface the agency links to, and this run must not
   silently keep refusing on a stale ground."
  [h]
  (-> (fetch-page (:host/probe-real h))
      (.then (fn [real]
               (-> (fetch-page (:host/probe-fake h))
                   (.then (fn [fake]
                            (cond
                              (or (:error real) (:error fake))
                              {:refuse (str "could not probe " (:host/name h) " — "
                                            (or (:error real) (:error fake)))}

                              (and (= (:status real) (:status fake))
                                   (= (count (:body real)) (count (:body fake)))
                                   (= (page-title (:body real)) (page-title (:body fake))))
                              {:indistinguishable true
                               :status (:status real)
                               :bytes (count (:body real))
                               :title (page-title (:body real))}

                              :else
                              {:refuse
                               (str (:host/name h)
                                    " now DISTINGUISHES a real law id from a fabricated one ("
                                    (:status real) "/" (count (:body real)) "B vs "
                                    (:status fake) "/" (count (:body fake))
                                    "B). facts.edn describes it as indistinguishable; "
                                    "re-measure it and update the register.")}))))))))

(defn- measure-forbidden-origin
  "The sitemap's host. Registered as serving nothing; probed so the claim
   stays measured rather than becoming folklore."
  [h]
  (-> (fetch-page (:host/probe h))
      (.then (fn [r]
               (cond
                 (:error r) {:refuse (str "could not probe " (:host/probe h) " — " (:error r))}
                 (= (:status r) (:host/expect-status h)) {:status (:status r)}
                 :else
                 {:refuse (str (:host/name h) " answered " (:status r)
                               ", register says " (:host/expect-status h)
                               ". If this origin has opened up, the sitemap note needs re-measuring.")})))))

;; ── statute check ──────────────────────────────────────────────────────────

(def ^:private live-token "None")
(def ^:private repeal-tokens #{"Repeal" "LossOfEffectiveness"})

(defn- read-repeal
  "repeal_status is the STRING \"None\" for a law in force. Neither truthiness
   nor a nil-comparison can read this field; only the literals can. Returns
   :in-force, :repealed, or :unknown -- and :unknown is REFUSED upstream,
   never guessed."
  [v]
  (cond (= v live-token) :in-force
        (contains? repeal-tokens v) :repealed
        :else :unknown))

(defn- check-statute [e api-base]
  (-> (fetch-json (str api-base (:statute/law-id e)))
      (.then
       (fn [r]
         (cond
           (:error r) {:reason :unreachable :detail (:error r)}

           (= 404 (:status r))
           {:reason :law-missing
            :detail (str (:statute/law-id e) " does not resolve")}

           (not= 200 (:status r))
           {:reason :status :detail (str "HTTP " (:status r))}

           (nil? (:json r))
           {:reason :unparseable :detail "response was not JSON"}

           :else
           (let [rev (get-in r [:json :revision_info])
                 title (:law_title rev)
                 repeal (read-repeal (:repeal_status rev))]
             (cond
               (not= title (:statute/title e))
               {:reason :law-title-changed
                :detail (str "API says " (pr-str title)
                             ", register says " (pr-str (:statute/title e)))}

               (= repeal :unknown)
               {:reason :repeal-unreadable
                :detail (str "repeal_status was " (pr-str (:repeal_status rev))
                             ", which is neither " (pr-str live-token)
                             " nor one of " (pr-str repeal-tokens))}

               (not= (= repeal :repealed) (boolean (:statute/repealed? e)))
               {:reason :repeal-changed
                :detail (str "API says " (name repeal)
                             ", register says :statute/repealed? "
                             (boolean (:statute/repealed? e)))}

               :else
               {:reason :ok
                :detail (str (:law_num (get-in r [:json :law_info])) " · " (name repeal))})))))))

;; ── page check ─────────────────────────────────────────────────────────────

(defn- check-page [e missing-text]
  (-> (fetch-page (:page/url e))
      (.then
       (fn [r]
         (cond
           (:error r) {:reason :unreachable :detail (:error r)}

           (not= 200 (:status r))
           {:reason :status :detail (str "HTTP " (:status r) " at " (:page/url e))}

           :else
           (let [title (page-title (:body r))
                 text (de-tag (:body r))
                 needle (:page/must-contain e)]
             (cond
               (not= title (:page/title e))
               {:reason :title
                :detail (str "live title " (pr-str title)
                             ", register says " (pr-str (:page/title e)))}

               ;; Checked BEFORE presence. A needle that is on the missing
               ;; page cannot distinguish this page from a deleted one, so
               ;; finding it here proves nothing -- and reporting a pass on it
               ;; would be the exact failure this file exists to prevent.
               (str/includes? missing-text needle)
               {:reason :needle-on-404
                :detail (str (pr-str needle)
                             " is on this host's 404 body; it no longer distinguishes "
                             "a live page from a deleted one")}

               (not (str/includes? text needle))
               {:reason :needle-missing
                :detail (str (pr-str needle) " is not on the page")}

               :else {:reason :ok :detail (str (count (:body r)) " B")})))))))

(defn- check-redirect
  "The recorded :page/via is what the agency still publishes. Assert it still
   behaves as recorded: still redirects (or still does not), and still lands
   where the register says. A via-path that starts answering directly, or
   lands somewhere new, has changed the citation."
  [e]
  (if-not (:page/via e)
    (js/Promise.resolve {:reason :ok :detail "no via-path recorded"})
    (-> (fetch-head (:page/via e))
        (.then
         (fn [r]
           (cond
             (:error r) {:reason :unreachable :detail (:error r)}

             (and (:page/redirects? e) (not (#{301 302 307 308} (:status r))))
             {:reason :redirect-vanished
              :detail (str (:page/via e) " answered " (:status r)
                           " directly; register records it as a redirect")}

             (and (:page/redirects? e)
                  (not= (:location r) (:page/url e)))
             {:reason :redirect-changed
              :detail (str "now lands at " (pr-str (:location r))
                           " (Location: " (pr-str (:location-raw r)) ")"
                           ", register says " (pr-str (:page/url e)))}

             :else {:reason :ok :detail (str (:status r) " -> " (:page/url e))}))))))

;; ── self-tests ─────────────────────────────────────────────────────────────
;;
;; Each asserts the REASON. A test that only asserted "not :ok" would count a
;; timeout, a typo, or an unrelated outage as a demonstration that the check
;; works.

(defn- self-tests [api-base missing-text page-host-name]
  (let [want (fn [label expected p]
               (.then p (fn [r]
                          (let [got (:reason r)]
                            {:label label :expected expected :got got
                             :ok? (= got expected)
                             :detail (:detail r)}))))]
    (js/Promise.all
     #js
     [;; 1. A fabricated law id must be reported missing -- the property the
        ;; SPA host does not have, and the reason statutes are resolved here.
      (want "fabricated law id -> :law-missing" :law-missing
            (check-statute {:statute/law-id "999AC9999999999"
                            :statute/title "存在しない法律"
                            :statute/repealed? false} api-base))

      ;; 2. A real, in-force law must pass. Without this, test 1 would also
      ;;    pass against a verifier that reported everything missing.
      (want "real in-force law -> :ok" :ok
            (check-statute {:statute/law-id "423AC0000000122"
                            :statute/title "東日本大震災復興特別区域法"
                            :statute/repealed? false} api-base))

      ;; 3. THE TRUTHY-STRING TRAP. A law in force carries repeal_status
      ;;    "None". If the reader used truthiness this returns :repeal-changed
      ;;    for a law that is in force -- so test 2 passing is what proves the
      ;;    literal comparison, and this asserts the same thing from the other
      ;;    side: declaring the in-force law repealed must be CAUGHT.
      (want "in-force law declared repealed -> :repeal-changed" :repeal-changed
            (check-statute {:statute/law-id "423AC0000000122"
                            :statute/title "東日本大震災復興特別区域法"
                            :statute/repealed? true} api-base))

      ;; 4. The repealed control, declared live, must be caught. e-Gov serves
      ;;    it at 200 with full text, so only the repeal field can catch it.
      (want "repealed rule declared live -> :repeal-changed" :repeal-changed
            (check-statute {:statute/law-id "423M60000100151"
                            :statute/title "厚生労働省関係東日本大震災復興特別区域法施行規則"
                            :statute/repealed? false} api-base))

      ;; 5. ...and declared repealed, it must pass. Tests 4 and 5 together are
      ;;    what make the repeal check a check rather than a constant.
      (want "repealed rule declared repealed -> :ok" :ok
            (check-statute {:statute/law-id "423M60000100151"
                            :statute/title "厚生労働省関係東日本大震災復興特別区域法施行規則"
                            :statute/repealed? true} api-base))

      ;; 6. A real law id with the wrong title must be caught -- otherwise a
      ;;    register entry could name one law and cite another.
      (want "right id, wrong title -> :law-title-changed" :law-title-changed
            (check-statute {:statute/law-id "423AC0000000122"
                            :statute/title "会計法"
                            :statute/repealed? false} api-base))

      ;; 7. A fabricated page must be caught by status.
      (want "fabricated page -> :status" :status
            (check-page {:page/url (str "https://" page-host-name
                                        "/topics/cat-114/zzz-no-such-page-4d1e7b/")
                         :page/title "どれでもない"
                         :page/must-contain "存在しない文字列"}
                        missing-text))

      ;; 8. A NEEDLE THAT IS ON THE 404 BODY must be REFUSED, not passed. The
      ;;    needle here is the 調達情報 page's own <h1>, which really is on
      ;;    this host's missing-page body -- so this test exercises the real
      ;;    trap on the real page, not a synthetic one.
      (want "needle that is site chrome -> :needle-on-404" :needle-on-404
            (check-page {:page/url (str "https://" page-host-name "/topics/cat-93/sub-cat9-1/")
                         :page/title "調達情報 | 復興庁"
                         :page/must-contain "調達情報"}
                        missing-text))

      ;; 9. A live page with a needle that is genuinely absent must FAIL, not
      ;;    refuse -- :needle-missing and :needle-on-404 are different claims
      ;;    and must not collapse into each other.
      (want "absent needle on a live page -> :needle-missing" :needle-missing
            (check-page {:page/url (str "https://" page-host-name "/topics/cat-93/sub-cat9-1/")
                         :page/title "調達情報 | 復興庁"
                         :page/must-contain "この文字列はこのページにない9f3c"}
                        missing-text))

      ;; 10. A via-path pointed at the wrong destination must be caught.
      (want "via-path landing elsewhere -> :redirect-changed" :redirect-changed
            (check-redirect {:page/via (str "https://" page-host-name "/topics/000344.html")
                             :page/url "https://example.invalid/wrong"
                             :page/redirects? true}))

      ;; 11. ...and a via-path landing where the register says must PASS.
      ;;     Without this, test 10 also passes against a verifier that reports
      ;;     :redirect-changed unconditionally -- which is exactly what the
      ;;     first version of this file did, because the Location header is
      ;;     relative and was compared against an absolute URL. Ten green
      ;;     self-tests did not catch it; this one does.
      (want "via-path landing where recorded -> :ok" :ok
            (check-redirect {:page/via (str "https://" page-host-name "/topics/000344.html")
                             :page/url (str "https://" page-host-name "/topics/cat-11/cat-45/000344/")
                             :page/redirects? true}))

      ;; 12. A path that does NOT redirect, recorded as one that does, must be
      ;;     caught -- the direction that would let a stale path sit unnoticed.
      (want "direct path recorded as redirecting -> :redirect-vanished" :redirect-vanished
            (check-redirect {:page/via (str "https://" page-host-name "/topics/main-cat12/")
                             :page/url (str "https://" page-host-name "/topics/main-cat12/")
                             :page/redirects? true}))])))

;; ── main ───────────────────────────────────────────────────────────────────

(defn- refuse! [msg]
  (println)
  (println "REFUSED —" msg)
  (println "  exit 2: this run could not answer. That is not a pass.")
  (js/process.exit 2))

(defn- read-register
  "A register that will not parse is not a register that says nothing -- it is
   a run that could not ask. Exit 2, not an uncaught throw, because an
   uncaught throw exits 1, and 1 is reserved for a claim about the world."
  []
  (let [text (try (fs/readFileSync "facts.edn" "utf8")
                  (catch :default e
                    (refuse! (str "facts.edn could not be read — " e))))
        parsed (try (edn/read-string text)
                    (catch :default e
                      (refuse! (str "facts.edn did not parse — " e
                                    ". The register is unreadable; nothing below ran."))))]
    (when-not (vector? parsed)
      (refuse! "facts.edn did not read as a vector of entity maps"))
    parsed))

(defn -main [& _argv]
  (let [sources (read-register)
        hosts (filterv :host/name sources)
        by-kind (into {} (map (juxt :host/kind identity)) hosts)
        page-host (:page by-kind)
        api-host (:statute-api by-kind)
        spa-host (:indistinguishable by-kind)
        origin-host (:non-authoritative-origin by-kind)
        statutes (filterv #(= :statute-api (:source/verify %)) sources)
        pages (filterv #(= :page (:source/verify %)) sources)]

    (println "── facts.edn — regulatory source register for JPN-RECONSTRUCTION")
    (println)
    (println (str "REGISTERED\t" (count statutes) " statutes\t" (count pages)
                  " pages\t" (count hosts) " hosts"))

    (when (or (nil? page-host) (nil? api-host) (nil? spa-host) (nil? origin-host))
      (refuse! "facts.edn is missing one of the four declared hosts; the checks below are written against all four"))
    (when (or (zero? (count statutes)) (zero? (count pages)))
      (refuse! "nothing to check. An empty register is not a clean register."))

    (-> (js/Promise.all #js [(measure-page-host page-host)
                             (measure-indistinguishable-host spa-host)
                             (measure-forbidden-origin origin-host)])
        (.then
         (fn [[pm spa origin]]
           (println)
           (println "── hosts (measured now, not read off the register)")
           (doseq [[h m] [[page-host pm] [spa-host spa] [origin-host origin]]]
             (println "  " (:host/name h)
                      (cond (:refuse m) (str "REFUSE — " (:refuse m))
                            (:indistinguishable m)
                            (str "indistinguishable as recorded — real and fabricated law id both "
                                 (:status m) " / " (:bytes m) " B / " (pr-str (:title m)))
                            (:missing-status m)
                            (str "404 discriminates — " (:missing-status m) " "
                                 (pr-str (:missing-title m)) ", body "
                                 (count (:missing-text m)) " chars of chrome")
                            :else (str (:status m) " as recorded"))))
           (when-let [r (some :refuse [pm spa origin])] (refuse! r))

           (-> (self-tests (:host/api-base api-host) (:missing-text pm) (:host/name page-host))
               (.then
                (fn [tests]
                  (let [tests (js->clj tests)]
                    (println)
                    (println "── self-tests (each asserts its REASON, not just a failure)")
                    (doseq [t tests]
                      (println (if (:ok? t) "  ok    " "  BROKEN")
                               (:label t)
                               (when-not (:ok? t) (str "— got " (:got t)
                                                       " (" (:detail t) ")"))))
                    (when-let [bad (seq (remove :ok? tests))]
                      (refuse! (str (count bad) " self-test(s) returned the wrong reason. "
                                    "The checks below cannot be trusted this run.")))

                    ;; sequential, to be a polite client of a government host
                    (-> (reduce
                         (fn [p e]
                           (.then p (fn [acc]
                                      (-> (sleep 150)
                                          (.then #(check-statute e (:host/api-base api-host)))
                                          (.then (fn [r] (conj acc [e r])))))))
                         (js/Promise.resolve [])
                         statutes)
                        (.then
                         (fn [statute-results]
                           (-> (reduce
                                (fn [p e]
                                  (.then p (fn [acc]
                                             (-> (sleep 150)
                                                 (.then #(check-page e (:missing-text pm)))
                                                 (.then (fn [pr]
                                                          (-> (sleep 150)
                                                              (.then #(check-redirect e))
                                                              (.then (fn [rr]
                                                                       (conj acc [e (if (= :ok (:reason pr)) rr pr)]))))))))))
                                (js/Promise.resolve [])
                                pages)
                               (.then
                                (fn [page-results]
                                  (let [all (concat statute-results page-results)
                                        refused (filterv #(#{:needle-on-404 :repeal-unreadable :unreachable :unparseable}
                                                           (:reason (second %))) all)
                                        failed (filterv #(not (#{:ok :needle-on-404 :repeal-unreadable
                                                                 :unreachable :unparseable}
                                                               (:reason (second %)))) all)]
                                    (println)
                                    (println "── statutes  (laws.e-gov.go.jp/api/2 — the surface that discriminates)")
                                    (doseq [[e r] statute-results]
                                      (println (case (:reason r) :ok "  PASS  " :needle-on-404 "  REFUSE" 
                                                     (:unreachable :unparseable :repeal-unreadable) "  REFUSE"
                                                     "  FAIL  ")
                                               (:statute/law-id e) (:statute/title e)
                                               (str "— " (:detail r))))
                                    (println)
                                    (println "── pages  (followed through redirects, needles re-tested against the live 404)")
                                    (doseq [[e r] page-results]
                                      (println (case (:reason r) :ok "  PASS  "
                                                     (:needle-on-404 :unreachable) "  REFUSE"
                                                     "  FAIL  ")
                                               (name (:source/id e))
                                               (str "— " (:detail r))))
                                    (println)
                                    (println (str "CHECKED\t" (count all) " of " (count all) " registered entries"))

                                    (cond
                                      (seq refused)
                                      (refuse! (str (count refused) " entr(ies) could not be answered: "
                                                    (str/join ", " (map #(str (or (:source/id (first %))
                                                                                  (:statute/law-id (first %)))
                                                                              " " (name (:reason (second %))))
                                                                        refused))))

                                      (seq failed)
                                      (do (println)
                                          (println (str "FAILED — " (count failed)
                                                        " citation(s) no longer agree with the register."))
                                          (println "  exit 1: the register is wrong about the world. Correct it against the authority.")
                                          (js/process.exit 1))

                                      :else
                                      (do (println)
                                          (println (str "OK — all " (count all)
                                                        " entries re-fetched and agreed with the register."))
                                          (js/process.exit 0)))))))))))))))))))

(apply -main *command-line-args*)
