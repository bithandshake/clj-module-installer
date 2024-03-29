
(ns module-installer.core.tests)

;; ----------------------------------------------------------------------------
;; ----------------------------------------------------------------------------

; @ignore
;
; @links
; [cljc-validator](https://github.com/mt-utilities/cljc-validator)
;
; @constant (map)
(def MODULE-ID-TEST
     {:f* keyword? :e* "must be a keyword!"})

; @ignore
;
; @links
; [cljc-validator](https://github.com/mt-utilities/cljc-validator)
;
; @constant (map)
(def INSTALLER-PROPS-TEST
     {:installer-f {:f*   fn?
                    :e*   ":installer-f must be a function!"}
      :priority    {:opt* true
                    :f*   integer?
                    :e*   ":priority must be an integer!"}
      :test-f      {:opt* true
                    :f*   fn?
                    :e*   ":test-f must be a function!"}})
