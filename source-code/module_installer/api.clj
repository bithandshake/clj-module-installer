
(ns module-installer.api
    (:require [module-installer.env          :as env]
              [module-installer.side-effects :as side-effects]))

;; ----------------------------------------------------------------------------
;; ----------------------------------------------------------------------------

; module-installer.side-effects
(def package-installed?    env/package-installed?)
(def require-installation? env/require-installation?)

; module-installer.side-effects
(def reg-installer!      side-effects/reg-installer!)
(def install-edn-file!   side-effects/install-edn-file!)
(def check-installation! side-effects/check-installation!)
