;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app.window
  (:use [penumbra.app.core])
  (:use [penumbra.opengl])
  (:use [penumbra.opengl.texture :only (create-texture-pool)])
  (:use [penumbra.opengl.core :only (*texture-pool*)])
  (:use [clojure.contrib.core :only (-?>)])
  (:require [penumbra.slate :as slate])
  (:require [penumbra.opengl.texture :as texture])
  (:require [penumbra.text :as text])
  (:import [org.lwjgl.opengl Display PixelFormat AWTGLCanvas])
  (:import [org.newdawn.slick.opengl InternalTextureLoader])
  (:import [java.awt Frame Canvas GridLayout Color])
  (:import [java.awt.event WindowAdapter]))

;;

(defstruct window-struct
  :texture-pool
  :frame
  :size)

;;Display Mode

(defn transform-display-mode [m]
  {:resolution [(.getWidth m) (.getHeight m)]
   :bpp (.getBitsPerPixel m)
   :fullscreen (.isFullscreenCapable m)
   :mode m})

(defn display-modes []
  (map transform-display-mode (Display/getAvailableDisplayModes)))

(defn current-display-mode []
  (transform-display-mode (Display/getDisplayMode)))

(defn display-mode!
  ([width height]
     (->>
      (display-modes)
      (filter #(= [width height] (:resolution %)))
      (sort-by :bpp)
      last
      display-mode!))
  ([mode]
     (Display/setDisplayMode (:mode mode))
     (apply viewport (concat [0 0] (:resolution mode)))))

(defn dimensions [w]
  (if-let [canvas (-?> w :frame deref (.getComponent 0))]
    [(.getWidth canvas) (.getHeight canvas)]
    (:resolution (transform-display-mode (Display/getDisplayMode)))))

(defn check-for-resize
  ([]
     (check-for-resize *window*))
  ([window]
     (let [[w h :as dim] (dimensions window)]
       (when (not= @(:size window) dim)
         (reset! (:size window) dim)
         (viewport 0 0 w h)
         (*callback-handler* :reshape (concat [0 0] dim))
         true))))

(defn vsync?
  ([] (vsync? *window*))
  ([window] @(:vsync window)))

(defn vsync!
  ([enabled]
     (vsync! *window* enabled))
  ([window enabled]
     (Display/setVSyncEnabled enabled)
     (reset! (:vsync? window) enabled)))

;;Frame

(defn enable-resizable! [app]
  (when (nil? @(-> app :window :frame))
    (let [window (:window app)
          frame (Frame.)
          canvas (AWTGLCanvas.)
          [w h] @(:size window)]
      (doto canvas
        (.setFocusable true)
        ;;(.setIgnoreRepaint true)
        (.setSize w h))
      (doto frame
        (.addWindowListener
         (proxy [WindowAdapter] []
           (windowOpened [event] (.requestFocus canvas))
           (windowClosing [event] (reset! (:stopped? app) true))))
        (.setTitle (Display/getTitle))
        ;;(.setIgnoreRepaint true)
        ;;(.setResizable true)
        (.setVisible true)
        (.add canvas)
        (.pack))
      (Display/setParent canvas)
      (reset! (:frame window) frame)
      nil)))

(defn disable-resizable! [app]
  (let [window (:window app)]
    (when-let [frame @(:frame window)]
      (.dispose frame)
      (Display/setParent nil)
      (reset! (:frame window) nil)
      nil)))

;;;

(defn create []
  (struct-map window-struct
    :drawable #(Display/getDrawable)
    :texture-pool (atom (create-texture-pool))
    :font-cache (atom {})
    :frame (atom nil)
    :size (atom [800 600])
    :vsync? (atom false)))

(defn init
  ([]
     (init *window*))
  ([window]
     (when-not (Display/isCreated)
       (Display/setParent nil)
       (Display/create (-> (PixelFormat.) (.withSamples 4))))
     (apply display-mode! @(:size window))
     (blend-func :src-alpha :one-minus-src-alpha)
     (apply viewport @(:size window))
     window))

(defn destroy
  ([]
     (destroy *window*))
  ([window]
     (-> (InternalTextureLoader/get) .clear)
     (texture/destroy-textures (:textures @*texture-pool*))
     (when-let [f @(:frame window)]
       (.dispose f))
     (Display/destroy)
     window))

(defmacro with-window [window & body]
  `(binding [*window* ~window
             *texture-pool* (:texture-pool ~window)
             text/*font-cache* (:font-cache ~window)]
     ~@body))

