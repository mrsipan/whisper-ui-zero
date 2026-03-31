(ns whisper.ui
  (:require
   ["https://cdn.jsdelivr.net/npm/eucalypt@0.0.3/+esm" :refer [render atom]]
            ; ["./squint/string.js" :as str]
            ))

;; --- State Management ---
(defonce App-State (atom {:recording? false
                      :chunks []}))

(def server-url "http://localhost:8080/inference")

;; --- Audio Logic ---
(defn send-to-server! [blob]
  (let [form-data (js/FormData.)]
    (.append form-data "file" blob "chunk.webm")
    (-> (js/fetch server-url #js {:method "POST" :body form-data})
        (.then (fn [res] (.json res)))
        (.then (fn [data]
                 (swap! App-State update :chunks conj (.-text data))))
        (.catch (fn [err] (js/console.error "Upload error:" err))))))

(defn create-recorder [stream]
  (let [recorder (js/MediaRecorder. stream)]
    (set! (.-ondataavailable recorder)
          (fn [e] (when (and (:recording? @App-State) (> (.-size (.-data e)) 0))
                    (send-to-server! (.-data e)))))
    recorder))

(def recorders (atom []))

(defn stop-streaming! []
  (swap! App-State assoc :recording? false)
  (doseq [r @recorders] (.stop r))
  (reset! recorders []))

(defn start-streaming! []
  (swap! App-State assoc :recording? true :chunks [])
  (-> (.getUserMedia js/navigator.mediaDevices #js {:audio #js {:sampleRate 16000}})
      (.then (fn [stream]
               (let [r-a (create-recorder stream)
                     r-b (create-recorder stream)]
                 (reset! recorders [r-a r-b])

                 ;; Recorder A: 5s chunks, restarts every 5s
                 (.start r-a)
                 (js/setInterval #(when (:recording? @App-State) (.stop r-a) (.start r-a)) 5000)

                 ;; Recorder B: 5s chunks, starts after 3s (2s overlap)
                 (js/setTimeout
                  #(when (:recording? @App-State)
                     (.start r-b)
                     (js/setInterval (fn [] (.stop r-b) (.start r-b)) 5000))
                  3000))))))

;; --- UI Components (Hiccup) ---
(defn app-view []
  (let [{:keys [recording? chunks]} @App-State]
    [:div {:class "has-text-centered":style {:font-family "sans-serif" :padding "20px"}}
     [:h1 "Whisper.cpp Live Stream"]

     [:button {:on-click (if recording? stop-streaming! start-streaming!)
               :style {:background (if recording? "#ff4444" "#44cc44")
                       :color "white" :padding "10px 20px" :border "none" :border-radius "4px"}}
      (if recording? "Stop Recording" "Start Recording")]

     [:div {:style {:margin-top "20px" :border "1px solid #ccc" :padding "10px" :min-height "200px"}}
      [:h3 "Transcriptions (Overlapping Segments):"]
      (if (empty? chunks)
        [:p {:style {:color "#888"}} "Silence..."]
        (for [text chunks]
          [:p {:key text :style {:border-bottom "1px solid #eee" :padding "5px 0"}} text]))]]))

;; --- Mount ---
; (render [app-view] (js/document.getElementById "app"))
(render
  [app-view]
  (or
    (js/document.getElementById "app")
    (doto (js/document.createElement "div")
      (aset "id" "app")
      (js/document.body.prepend))))
