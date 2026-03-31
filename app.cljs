(ns whisper-app.core
  ; (:require [eucalypt.core :refer [render atom]]
  ;           [clojure.string :as str]))

  (:require
   ["https://cdn.jsdelivr.net/npm/eucalypt@0.0.3/+esm" :refer  [render atom]]))

;; --- Configuration & State ---
(def server-url "http://localhost:8080/inference")
(def sample-rate 16000)
(def chunk-size 5)   ; Total chunk duration in seconds
(def overlap-sec 2)  ; Overlap duration in seconds

(defonce state (atom {:recording? false :transcriptions []}))
(defonce stream-refs (atom {:context nil :processor nil :buffer-a [] :buffer-b []}))

;; --- WAV Encoding Utility ---
(defn encode-wav [samples]
  (let [buffer (js/ArrayBuffer. (+ 44 (* (.-length samples) 2)))
        view (js/DataView. buffer)
        set-str (fn [off s] (dotimes [i (.-length s)] (.setUint8 view (+ off i) (.charCodeAt s i))))]
    (set-str 0 "RIFF")
    (.setUint32 view 4 (- (.-byteLength buffer) 8) true)
    (set-str 8 "WAVE")
    (set-str 12 "fmt ")
    (.setUint32 view 16 16 true)
    (.setUint16 view 20 1 true)  ; PCM
    (.setUint16 view 22 1 true)  ; Mono
    (.setUint32 view 24 sample-rate true)
    (.setUint32 view 28 (* sample-rate 2) true)
    (.setUint16 view 32 2 true)
    (.setUint16 view 34 16 true)
    (set-str 36 "data")
    (.setUint32 view 40 (* (.-length samples) 2) true)
    (dotimes [i (.-length samples)]
      (let [s (js/Math.max -1 (js/Math.min 1 (aget samples i)))
            v (if (< s 0) (* s 0x8000) (* s 0x7FFF))]
        (.setInt16 view (+ 44 (* i 2)) v true)))
    (js/Blob. #js [buffer] #js {:type "audio/wav"})))

;; --- Backend Communication ---
(defn send-audio! [blob]
  (let [fd (js/FormData.)]
    (.append fd "file" blob "stream.wav")
    (-> (js/fetch server-url #js {:method "POST" :body fd})
        (.then #(.json %))
        (.then #(swap! state update :transcriptions conj (.-text %)))
        (.catch #(js/console.error "Server Error:" %)))))

;; --- Audio Processing ---
(defn stop-recording! []
  (let [{:keys [context processor]} @stream-refs]
    (when processor (.disconnect processor))
    (when context (.close context))
    (swap! state assoc :recording? false)
    (reset! stream-refs {:context nil :processor nil :buffer-a [] :buffer-b []})))

(defn start-recording! []
  (-> (.getUserMedia js/navigator.mediaDevices #js {:audio true})
      (.then (fn [stream]
               (let [ctx (js/AudioContext. #js {:sampleRate sample-rate})
                     src (.createMediaStreamSource ctx stream)
                     proc (.createScriptProcessor ctx 4096 1 1)]
                 (swap! state assoc :recording? true :transcriptions [])
                 (swap! stream-refs assoc :context ctx :processor proc)
                 (set! (.-onaudioprocess proc)
                       (fn [e]
                         (let [input (.getChannelData (.-inputBuffer e) 0)
                               max-samples (* sample-rate chunk-size)
                               offset-samples (* sample-rate (- chunk-size overlap-sec))]
                           ;; Fill Buffer A (Main)
                           (swap! stream-refs update :buffer-a #(into % input))
                           ;; Fill Buffer B (Overlap - starts after offset)
                           (when (> (count (:buffer-a @stream-refs)) offset-samples)
                             (swap! stream-refs update :buffer-b #(into % input)))
                           ;; When Buffer A is full, send and swap
                           (when (>= (count (:buffer-a @stream-refs)) max-samples)
                             (send-audio! (encode-wav (js/Float32Array.from (:buffer-a @stream-refs))))
                             (swap! stream-refs assoc :buffer-a (:buffer-b @stream-refs) :buffer-b [])))))
                 (.connect src proc)
                 (.connect proc (.-destination ctx)))))
      (.catch #(js/alert "Mic access denied"))))

;; --- UI View ---
(defn whisper-ui []
  (let [{:keys [recording? transcriptions]} @state]
    [:div {:class  "has-text-centered" :style {:padding "2rem" :max-width "600px" :margin "auto"}}
     [:h1 "Whisper.cpp Live Transcriber"]
     [:button {:on-click (if recording? stop-recording! start-recording!)
               :style {:padding "12px 24px" :cursor "pointer"
                       :background (if recording? "#ef4444" "#22c55e") :color "white" :border "none"}}
      (if recording? "⏹ Stop Stream" "🎙 Start Streaming")]
     [:div {:style {:margin-top "20px" :background "#f3f4f6" :padding "1rem" :min-height "300px"}}
      (if (empty? transcriptions)
        [:p "Listening..."]
        (for [t transcriptions]
          [:p {:style {:border-bottom "1px solid #ddd" :padding "8px 0"}} t]))]]))


(render
  [whisper-ui]
  (or
    (js/document.getElementById "app")
    (doto (js/document.createElement "div")
      (aset "id" "app")
      (js/document.body.prepend))))

