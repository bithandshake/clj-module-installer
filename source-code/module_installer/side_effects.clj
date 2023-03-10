
(ns module-installer.side-effects
    (:require [git.api                     :as git]
              [io.api                      :as io]
              [logger.api                  :as logger]
              [map.api                     :as map]
              [module-installer.config     :as config]
              [module-installer.env        :as env]
              [module-installer.patterns   :as patterns]
              [module-installer.prototypes :as prototypes]
              [module-installer.state      :as state]
              [noop.api                    :refer [return]]
              [pattern.api                 :as p]
              [time.api                    :as time]
              [vector.api                  :as vector]))

;; ----------------------------------------------------------------------------
;; ----------------------------------------------------------------------------

(defn reg-installer!
  ; @description
  ; Registers a module installer package that will be applied when the 'check-installation!'
  ; function next called.
  ;
  ; The :installer-f function's return value will be passed to the :test-f function,
  ; and the :test-f function's return value will be evaluted as a boolean.
  ; If false the installation will be qualified as an installation failure,
  ; and the package will be reinstalled when the 'check-installation!' next called.
  ;
  ; If you don't pass the :test-f function, the :installer-f function's return
  ; value will be evaluted as a boolean.
  ;
  ; By passing the {:priority ...} property, you can control the package installation
  ; order. As higher is the priority value, the installer function will be applied as sooner.
  ;
  ; @param (keyword) package-id
  ; @param (map) package-props
  ; {:installer-f (function)
  ;  :priority (integer)(opt)
  ;   Default: 0
  ;  :test-f (function)(opt)
  ;   Default: boolean}
  ;
  ; @usage
  ; (reg-installer! :my-package {...})
  ;
  ; @usage
  ; (defn my-package-f [] ...)
  ; (reg-installer! :my-package {:installer-f my-package-f})
  [package-id {:keys [preinstaller?] :as package-props}]
  (and (p/valid? package-id    {:test* {:f* keyword? :e* "package-id must be a keyword!"}})
       (p/valid? package-props {:pattern* patterns/PACKAGE-PROPS-PATTERN :prefix* "package-props"})
       (let [package-props (prototypes/package-props-prototype package-props)]
            (swap! state/INSTALLERS assoc package-id package-props))))

;; ----------------------------------------------------------------------------
;; ----------------------------------------------------------------------------

(defn install-edn-file!
  ; @description
  ; Creates and EDN file onto the given filepath (only if it does not exist),
  ; and when creating, writes the body and/or the header into the created file.
  ;
  ; @param (string) filepath
  ; @param (*) body
  ; @param (string)(opt) header
  ;
  ; @usage
  ; (install-edn-file! "my-file.edn" {...})
  ;
  ; @usage
  ; (install-edn-file! "my-file.edn" {...} "My header\n...")
  ;
  ; @usage
  ; (install-edn-file! "my-file.edn" nil "My header\n...")
  ;
  ; @return (boolean)
  ([filepath body]
   (if-not (io/file-exists?    filepath)
           (io/write-edn-file! filepath body {:create? true})
           (return :edn-file-already-exists)))

  ([filepath body header]
   (if-not (io/file-exists?           filepath)
           (and (io/write-edn-file!   filepath body   {:create? true})
                (io/write-edn-header! filepath header {:create? true}))
           (return :edn-file-already-exists))))

;; ----------------------------------------------------------------------------
;; ----------------------------------------------------------------------------

(defn- installation-error-catched
  ; @ignore
  ;
  ; @description
  ; Prints the given error message to the console, writes the error-message
  ; into the installation error log and exits the server.
  ;
  ; @param (keyword) package-id
  ; @param (*) error-message
  ;
  ; @usage
  ; (installation-error-catched :my-package "Something went wrong ...")
  [package-id error-message]
  (logger/write! config/INSTALLATION-ERRORS-FILEPATH (str "\n" package-id "\n" error-message "\n"))
  ; ***
  (println "module-installer error catched in package installer:" package-id)
  (println error-message)
  (println "module-installer exiting server ...")
  (System/exit 0))

;; ----------------------------------------------------------------------------
;; ----------------------------------------------------------------------------

(defn print-installation-state!
  ; @usage
  ; (print-installation-state!)
  []
  (let [first-package-installed-at (get-first-package-installed-at)
        installed-package-count    (get-installed-package-count)
        first-package-installed-at (time/timestamp-string->date-time first-package-installed-at)]
       (println "module-installer installed" installed-package-count "packages since" first-package-installed-at)))

;; ----------------------------------------------------------------------------
;; ----------------------------------------------------------------------------

(defn- install-package!
  ; @ignore
  ;
  ; @param (keyword) package-id
  [package-id]
  ; Applying the installer functions, evaluting its result as boolean and storing the result,
  ; in the installation log file
  (try (let [result ((-> @state/INSTALLERS package-id :installer-f))
             output ((-> @state/INSTALLERS package-id :test-f) result)]
            (when   result (println "module-installer installed:" package-id)
                           (swap! state/INSTALLATION-STATE update :installed-package-count inc))
            (if-not result (installation-error-catched package-id result))
            (io/swap-edn-file! config/INSTALLED-PACKAGES-FILEPATH assoc package-id
                               {:result output :installed-at (time/timestamp-string)}))
       (catch Exception e (installation-error-catched package-id e))))

(defn- install-packages!
  ; @ignore
  ;
  ; @param (map) packages
  []
  ; ...
  (println "module-installer installing packages ...")
  ; Initializing the install-handler (creating the installation log file and adding it to .gitignore)
  (reset! state/INSTALLATION-STATE {:installed-package-count 0})
  (io/create-file! config/INSTALLED-PACKAGES-FILEPATH)
  (git/ignore!     config/INSTALLED-PACKAGES-FILEPATH  {:group "module-installer"})
  (git/ignore!     config/INSTALLATION-ERRORS-FILEPATH {:group "module-installer"})
  ; Reading the installation log file.
  (let [installed-packages (io/read-edn-file config/INSTALLED-PACKAGES-FILEPATH {:warn? false})]
       (letfn [(f [package-id]
                  ; If the package is already installed, but the result stored as false,
                  ; reinstalling it
                  (when-let [reinstall? (-> installed-packages package-id :result false?)]
                            (println "module-installer reinstalling:" package-id "...")
                            (install-package! package-id))
                  ; If the package is not installed yet, installing it
                  (when-let [install? (-> installed-packages package-id :result nil?)]
                            (println "module-installer installing:" package-id "...")
                            (install-package! package-id)))]
              ; Iterating over the registered installer functions ...
              (let [installation-order (-> @state/INSTALLERS map/get-keys (vector/order-items-by :priority))]
                   (doseq [package-id installation-order]
                          (f package-id)))
              ; ...
              (let [installed-package-count (get @state/INSTALLATION-STATE :installed-package-count)]
                   (println "module-installer successfully installed:" installed-package-count "packages")
                   (println "module-installer exiting server ...")
                   (System/exit 0)))))

;; ----------------------------------------------------------------------------
;; ----------------------------------------------------------------------------

(defn check-installation!
  ; @usage
  ; (check-installation!)
  []
  (if (env/require-installation?)
      (install-packages!)
      (print-installation-state!)))
