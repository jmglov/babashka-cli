(ns babashka.cli-test
  (:require
   [babashka.cli :as cli]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]))

(defn normalize-filename [s]
  (str/replace s "\\" "/"))

(defn regex? [x]
  #?(:clj (instance? java.util.regex.Pattern x)
     :cljs (regexp? x)))

(defn submap?
  "Is m1 a subset of m2? Taken from clj-kondo."
  [m1 m2]
  (cond
    (and (map? m1) (map? m2))
    (every? (fn [[k v]] (and (contains? m2 k)
                             (if (or (identical? k :filename)
                                     (identical? k :file))
                               (if (regex? v)
                                 (re-find v (normalize-filename (get m2 k)))
                                 (= (normalize-filename v)
                                    (normalize-filename (get m2 k))))
                               (submap? v (get m2 k)))))
            m1)
    (regex? m1)
    (re-find m1 m2)
    :else (= m1 m2)))

(defn foo
  {:babashka/cli {:coerce {:b parse-long}}}
  [{:keys [b]}]
  {:b b})

(deftest parse-opts-test
  (let [res (cli/parse-opts ["foo" ":b" "1"])]
    (is (submap? '{:b "1"} res))
    (is (submap? {:org.babashka/cli {:cmds ["foo"]}} (meta res)))
    (is (submap? ["foo"] (cli/commands res))))
  (is (submap? '{:b 1}
         (cli/parse-opts ["foo" ":b" "1"] {:coerce {:b parse-long}})))
  (is (submap? '{:b 1}
         (cli/parse-opts ["foo" "--b" "1"] {:coerce {:b parse-long}})))
  (is (submap? '{:boo 1}
               (cli/parse-opts ["foo" ":b" "1"] {:aliases {:b :boo}
                                                 :coerce {:boo parse-long}})))
  (is (submap? '{:boo 1 :foo true}
               (cli/parse-opts ["--boo=1" "--foo"]
                               {:coerce {:boo parse-long}})))
  (is (try (cli/parse-opts [":b" "dude"] {:coerce {:b :long}})
           false
           (catch Exception e
             (= {:input "dude", :coerce-fn :long} (ex-data e)))))
  (is (submap? {:a [1 1]}
               (cli/parse-opts ["-a" "1" "-a" "1"] {:collect {:a []} :coerce {:a :long}}))))

(deftest parse-opts-collect-test
  (is (submap? '{:paths ["src" "test"]}
               (cli/parse-opts [":paths" "src" "test"] {:collect {:paths []}})))
  (is (submap? {:paths #{"src" "test"}}
               (cli/parse-opts [":paths" "src" "test"] {:collect {:paths #{}}})))
  (is (submap? {:verbose [true true true]}
               (cli/parse-opts ["-v" "-v" "-v"] {:aliases {:v :verbose}
                                                 :collect {:verbose []}}))))

(deftest parse-opts-remaining-test
  (is (submap? {:foo true} (cli/parse-opts ["--foo" "--"])))
  (let [res (cli/parse-opts ["--foo" "--" "a"])]
    (is (submap? {:foo true} res))
    (is (submap? {:org.babashka/cli {:remaining ["a"]}} (meta res)))
    (is (submap? ["a"] (cli/remaining res)))))
