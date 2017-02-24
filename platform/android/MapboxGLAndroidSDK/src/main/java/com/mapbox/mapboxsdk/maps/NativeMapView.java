package com.mapbox.mapboxsdk.maps;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Surface;

import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.Polygon;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.constants.MapboxConstants;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.ProjectedMeters;
import com.mapbox.mapboxsdk.storage.FileSource;
import com.mapbox.mapboxsdk.style.layers.CannotAddLayerException;
import com.mapbox.mapboxsdk.style.layers.Layer;
import com.mapbox.mapboxsdk.style.sources.CannotAddSourceException;
import com.mapbox.mapboxsdk.style.sources.Source;
import com.mapbox.services.commons.geojson.Feature;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import timber.log.Timber;

;

// Class that wraps the native methods for convenience
final class NativeMapView {

  // Flag to indicating destroy was called
  private boolean destroyed = false;

  // Holds the pointer to JNI NativeMapView
  private long nativePtr = 0;

  // Used for callbacks
  private MapView mapView;

  //Hold a reference to prevent it from being GC'd as long as it's used on the native side
  private final FileSource fileSource;

  // Device density
  private final float pixelRatio;

  // Listeners for Map change events
  private CopyOnWriteArrayList<MapView.OnMapChangedListener> onMapChangedListeners;

  // Listener invoked to return a bitmap of the map
  private MapboxMap.SnapshotReadyCallback snapshotReadyCallback;

  //
  // Static methods
  //

  static {
    System.loadLibrary("mapbox-gl");
  }

  //
  // Constructors
  //

  public NativeMapView(MapView mapView) {
    Context context = mapView.getContext();
    fileSource = FileSource.getInstance(context);

    pixelRatio = context.getResources().getDisplayMetrics().density;
    int availableProcessors = Runtime.getRuntime().availableProcessors();
    ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    activityManager.getMemoryInfo(memoryInfo);
    long totalMemory = memoryInfo.availMem;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      totalMemory = memoryInfo.totalMem;
    }

    if (availableProcessors < 0) {
      throw new IllegalArgumentException("availableProcessors cannot be negative.");
    }

    if (totalMemory < 0) {
      throw new IllegalArgumentException("totalMemory cannot be negative.");
    }
    onMapChangedListeners = new CopyOnWriteArrayList<>();
    this.mapView = mapView;

    nativeInitialize(this, fileSource, pixelRatio, availableProcessors, totalMemory);
  }

  //
  // Methods
  //

  private boolean isDestroyedOn(String callingMethod) {
    if (destroyed && !TextUtils.isEmpty(callingMethod)) {
      Timber.e(String.format(MapboxConstants.MAPBOX_LOCALE,
        "You're calling `%s` after the `MapView` was destroyed, were you invoking it after `onDestroy()`?",
        callingMethod));
    }
    return destroyed;
  }

  public void destroy() {
    nativeDestroy();
    mapView = null;
    destroyed = true;
  }

  public void initializeDisplay() {
    if (isDestroyedOn("initializeDisplay")) {
      return;
    }
    nativeInitializeDisplay();
  }

  public void terminateDisplay() {
    if (isDestroyedOn("terminateDisplay")) {
      return;
    }
    nativeTerminateDisplay();
  }

  public void initializeContext() {
    if (isDestroyedOn("initializeContext")) {
      return;
    }
    nativeInitializeContext();
  }

  public void terminateContext() {
    if (isDestroyedOn("terminateContext")) {
      return;
    }
    nativeTerminateContext();
  }

  public void createSurface(Surface surface) {
    if (isDestroyedOn("createSurface")) {
      return;
    }
    nativeCreateSurface(surface);
  }

  public void destroySurface() {
    if (isDestroyedOn("destroySurface")) {
      return;
    }
    nativeDestroySurface();
  }

  public void update() {
    if (isDestroyedOn("update")) {
      return;
    }
    nativeUpdate();
  }

  public void render() {
    if (isDestroyedOn("render")) {
      return;
    }
    nativeRender();
  }

  public void resizeView(int width, int height) {
    if (isDestroyedOn("resizeView")) {
      return;
    }
    width = (int) (width / pixelRatio);
    height = (int) (height / pixelRatio);

    if (width < 0) {
      throw new IllegalArgumentException("width cannot be negative.");
    }

    if (height < 0) {
      throw new IllegalArgumentException("height cannot be negative.");
    }

    if (width > 65535) {
      // we have seen edge cases where devices return incorrect values #6111
      Timber.e("Device returned an out of range width size, "
        + "capping value at 65535 instead of " + width);
      width = 65535;
    }

    if (height > 65535) {
      // we have seen edge cases where devices return incorrect values #6111
      Timber.e("Device returned an out of range height size, "
        + "capping value at 65535 instead of " + height);
      height = 65535;
    }
    nativeResizeView(width, height);
  }

  public void resizeFramebuffer(int fbWidth, int fbHeight) {
    if (isDestroyedOn("resizeFramebuffer")) {
      return;
    }
    if (fbWidth < 0) {
      throw new IllegalArgumentException("fbWidth cannot be negative.");
    }

    if (fbHeight < 0) {
      throw new IllegalArgumentException("fbHeight cannot be negative.");
    }

    if (fbWidth > 65535) {
      throw new IllegalArgumentException(
        "fbWidth cannot be greater than 65535.");
    }

    if (fbHeight > 65535) {
      throw new IllegalArgumentException(
        "fbHeight cannot be greater than 65535.");
    }
    nativeResizeFramebuffer(fbWidth, fbHeight);
  }

  public void setStyleUrl(String url) {
    if (isDestroyedOn("setStyleUrl")) {
      return;
    }
    nativeSetStyleUrl(url);
  }

  public String getStyleUrl() {
    if (isDestroyedOn("getStyleUrl")) {
      return null;
    }
    return nativeGetStyleUrl();
  }

  public void setStyleJson(String newStyleJson) {
    if (isDestroyedOn("setStyleJson")) {
      return;
    }
    nativeSetStyleJson(newStyleJson);
  }

  public String getStyleJson() {
    if (isDestroyedOn("getStyleJson")) {
      return null;
    }
    return nativeGetStyleJson();
  }

  public void cancelTransitions() {
    if (isDestroyedOn("cancelTransitions")) {
      return;
    }
    nativeCancelTransitions();
  }

  public void setGestureInProgress(boolean inProgress) {
    if (isDestroyedOn("setGestureInProgress")) {
      return;
    }
    nativeSetGestureInProgress(inProgress);
  }

  public void moveBy(double dx, double dy) {
    if (isDestroyedOn("moveBy")) {
      return;
    }
    moveBy(dx, dy, 0);
  }

  public void moveBy(double dx, double dy, long duration) {
    if (isDestroyedOn("moveBy")) {
      return;
    }
    nativeMoveBy(dx / pixelRatio, dy / pixelRatio, duration);
  }

  public void setLatLng(LatLng latLng) {
    if (isDestroyedOn("setLatLng")) {
      return;
    }
    setLatLng(latLng, 0);
  }

  public void setLatLng(LatLng latLng, long duration) {
    if (isDestroyedOn("setLatLng")) {
      return;
    }
    nativeSetLatLng(latLng.getLatitude(), latLng.getLongitude(), duration);
  }

  public LatLng getLatLng() {
    if (isDestroyedOn("")) {
      return new LatLng();
    }
    // wrap longitude values coming from core
    return nativeGetLatLng().wrap();
  }

  public void resetPosition() {
    if (isDestroyedOn("resetPosition")) {
      return;
    }
    nativeResetPosition();
  }

  public double getPitch() {
    if (isDestroyedOn("getPitch")) {
      return 0;
    }
    return nativeGetPitch();
  }

  public void setPitch(double pitch, long duration) {
    if (isDestroyedOn("setPitch")) {
      return;
    }
    nativeSetPitch(pitch, duration);
  }

  public void scaleBy(double ds) {
    if (isDestroyedOn("scaleBy")) {
      return;
    }
    scaleBy(ds, Double.NaN, Double.NaN);
  }

  public void scaleBy(double ds, double cx, double cy) {
    if (isDestroyedOn("scaleBy")) {
      return;
    }
    scaleBy(ds, cx, cy, 0);
  }

  public void scaleBy(double ds, double cx, double cy, long duration) {
    if (isDestroyedOn("scaleBy")) {
      return;
    }
    nativeScaleBy(ds, cx / pixelRatio, cy / pixelRatio, duration);
  }

  public void setScale(double scale) {
    if (isDestroyedOn("setScale")) {
      return;
    }
    setScale(scale, Double.NaN, Double.NaN);
  }

  public void setScale(double scale, double cx, double cy) {
    if (isDestroyedOn("setScale")) {
      return;
    }
    setScale(scale, cx, cy, 0);
  }

  public void setScale(double scale, double cx, double cy, long duration) {
    if (isDestroyedOn("setScale")) {
      return;
    }
    nativeSetScale(scale, cx / pixelRatio, cy / pixelRatio, duration);
  }

  public double getScale() {
    if (isDestroyedOn("getScale")) {
      return 0;
    }
    return nativeGetScale();
  }

  public void setZoom(double zoom) {
    if (isDestroyedOn("setZoom")) {
      return;
    }
    setZoom(zoom, 0);
  }

  public void setZoom(double zoom, long duration) {
    if (isDestroyedOn("setZoom")) {
      return;
    }
    nativeSetZoom(zoom, duration);
  }

  public double getZoom() {
    if (isDestroyedOn("getZoom")) {
      return 0;
    }
    return nativeGetZoom();
  }

  public void resetZoom() {
    if (isDestroyedOn("resetZoom")) {
      return;
    }
    nativeResetZoom();
  }

  public void setMinZoom(double zoom) {
    if (isDestroyedOn("setMinZoom")) {
      return;
    }
    nativeSetMinZoom(zoom);
  }

  public double getMinZoom() {
    if (isDestroyedOn("getMinZoom")) {
      return 0;
    }
    return nativeGetMinZoom();
  }

  public void setMaxZoom(double zoom) {
    if (isDestroyedOn("setMaxZoom")) {
      return;
    }
    nativeSetMaxZoom(zoom);
  }

  public double getMaxZoom() {
    if (isDestroyedOn("getMaxZoom")) {
      return 0;
    }
    return nativeGetMaxZoom();
  }

  public void rotateBy(double sx, double sy, double ex, double ey) {
    if (isDestroyedOn("rotateBy")) {
      return;
    }
    rotateBy(sx, sy, ex, ey, 0);
  }

  public void rotateBy(double sx, double sy, double ex, double ey,
                       long duration) {
    if (isDestroyedOn("rotateBy")) {
      return;
    }
    nativeRotateBy(sx / pixelRatio, sy / pixelRatio, ex, ey, duration);
  }

  public void setContentPadding(int[] padding) {
    if (isDestroyedOn("setContentPadding")) {
      return;
    }
    nativeSetContentPadding(
      padding[1] / pixelRatio,
      padding[0] / pixelRatio,
      padding[3] / pixelRatio,
      padding[2] / pixelRatio);
  }

  public void setBearing(double degrees) {
    if (isDestroyedOn("setBearing")) {
      return;
    }
    setBearing(degrees, 0);
  }

  public void setBearing(double degrees, long duration) {
    if (isDestroyedOn("setBearing")) {
      return;
    }
    nativeSetBearing(degrees, duration);
  }

  public void setBearing(double degrees, double cx, double cy) {
    if (isDestroyedOn("setBearing")) {
      return;
    }
    setBearing(degrees, cx, cy, 0);
  }

  public void setBearing(double degrees, double fx, double fy, long duration) {
    if (isDestroyedOn("setBearing")) {
      return;
    }
    nativeSetBearingXY(degrees, fx / pixelRatio, fy / pixelRatio, duration);
  }

  public double getBearing() {
    if (isDestroyedOn("getBearing")) {
      return 0;
    }
    return nativeGetBearing();
  }

  public void resetNorth() {
    if (isDestroyedOn("resetNorth")) {
      return;
    }
    nativeResetNorth();
  }

  public long addMarker(Marker marker) {
    if (isDestroyedOn("addMarker")) {
      return 0;
    }
    Marker[] markers = {marker};
    return nativeAddMarkers(markers)[0];
  }

  public long[] addMarkers(List<Marker> markers) {
    if (isDestroyedOn("addMarkers")) {
      return new long[] {};
    }
    return nativeAddMarkers(markers.toArray(new Marker[markers.size()]));
  }

  public long addPolyline(Polyline polyline) {
    if (isDestroyedOn("addPolyline")) {
      return 0;
    }
    Polyline[] polylines = {polyline};
    return nativeAddPolylines(polylines)[0];
  }

  public long[] addPolylines(List<Polyline> polylines) {
    if (isDestroyedOn("addPolylines")) {
      return new long[] {};
    }
    return nativeAddPolylines(polylines.toArray(new Polyline[polylines.size()]));
  }

  public long addPolygon(Polygon polygon) {
    if (isDestroyedOn("addPolygon")) {
      return 0;
    }
    Polygon[] polygons = {polygon};
    return nativeAddPolygons(polygons)[0];
  }

  public long[] addPolygons(List<Polygon> polygons) {
    if (isDestroyedOn("addPolygons")) {
      return new long[] {};
    }
    return nativeAddPolygons(polygons.toArray(new Polygon[polygons.size()]));
  }

  public void updateMarker(Marker marker) {
    if (isDestroyedOn("updateMarker")) {
      return;
    }
    LatLng position = marker.getPosition();
    Icon icon = marker.getIcon();
    nativeUpdateMarker(marker.getId(), position.getLatitude(), position.getLongitude(), icon.getId());
  }

  public void updatePolygon(Polygon polygon) {
    if (isDestroyedOn("updatePolygon")) {
      return;
    }
    nativeUpdatePolygon(polygon.getId(), polygon);
  }

  public void updatePolyline(Polyline polyline) {
    if (isDestroyedOn("updatePolyline")) {
      return;
    }
    nativeUpdatePolyline(polyline.getId(), polyline);
  }

  public void removeAnnotation(long id) {
    if (isDestroyedOn("removeAnnotation")) {
      return;
    }
    long[] ids = {id};
    removeAnnotations(ids);
  }

  public void removeAnnotations(long[] ids) {
    if (isDestroyedOn("removeAnnotations")) {
      return;
    }
    nativeRemoveAnnotations(ids);
  }

  public long[] queryPointAnnotations(RectF rect) {
    if (isDestroyedOn("queryPointAnnotations")) {
      return new long[] {};
    }
    return nativeQueryPointAnnotations(rect);
  }

  public void addAnnotationIcon(String symbol, int width, int height, float scale, byte[] pixels) {
    if (isDestroyedOn("addAnnotationIcon")) {
      return;
    }
    nativeAddAnnotationIcon(symbol, width, height, scale, pixels);
  }

  public void setVisibleCoordinateBounds(LatLng[] coordinates, RectF padding, double direction, long duration) {
    if (isDestroyedOn("setVisibleCoordinateBounds")) {
      return;
    }
    nativeSetVisibleCoordinateBounds(coordinates, padding, direction, duration);
  }

  public void onLowMemory() {
    if (isDestroyedOn("onLowMemory")) {
      return;
    }
    nativeOnLowMemory();
  }

  public void setDebug(boolean debug) {
    if (isDestroyedOn("setDebug")) {
      return;
    }
    nativeSetDebug(debug);
  }

  public void cycleDebugOptions() {
    if (isDestroyedOn("cycleDebugOptions")) {
      return;
    }
    nativeCycleDebugOptions();
  }

  public boolean getDebug() {
    if (isDestroyedOn("getDebug")) {
      return false;
    }
    return nativeGetDebug();
  }

  public void setEnableFps(boolean enable) {
    if (isDestroyedOn("setEnableFps")) {
      return;
    }
    nativeSetEnableFps(enable);
  }

  public boolean isFullyLoaded() {
    if (isDestroyedOn("isFullyLoaded")) {
      return false;
    }
    return nativeIsFullyLoaded();
  }

  public void setReachability(boolean status) {
    if (isDestroyedOn("setReachability")) {
      return;
    }
    nativeSetReachability(status);
  }

  public double getMetersPerPixelAtLatitude(double lat) {
    if (isDestroyedOn("getMetersPerPixelAtLatitude")) {
      return 0;
    }
    return nativeGetMetersPerPixelAtLatitude(lat, getZoom());
  }

  public ProjectedMeters projectedMetersForLatLng(LatLng latLng) {
    if (isDestroyedOn("projectedMetersForLatLng")) {
      return null;
    }
    return nativeProjectedMetersForLatLng(latLng.getLatitude(), latLng.getLongitude());
  }

  public LatLng latLngForProjectedMeters(ProjectedMeters projectedMeters) {
    if (isDestroyedOn("latLngForProjectedMeters")) {
      return new LatLng();
    }
    return nativeLatLngForProjectedMeters(projectedMeters.getNorthing(),
      projectedMeters.getEasting()).wrap();
  }

  public PointF pixelForLatLng(LatLng latLng) {
    if (isDestroyedOn("pixelForLatLng")) {
      return new PointF();
    }
    PointF pointF = nativePixelForLatLng(latLng.getLatitude(), latLng.getLongitude());
    pointF.set(pointF.x * pixelRatio, pointF.y * pixelRatio);
    return pointF;
  }

  public LatLng latLngForPixel(PointF pixel) {
    if (isDestroyedOn("latLngForPixel")) {
      return new LatLng();
    }
    return nativeLatLngForPixel(pixel.x / pixelRatio, pixel.y / pixelRatio).wrap();
  }

  public double getTopOffsetPixelsForAnnotationSymbol(String symbolName) {
    if (isDestroyedOn("getTopOffsetPixelsForAnnotationSymbol")) {
      return 0;
    }
    return nativeGetTopOffsetPixelsForAnnotationSymbol(symbolName);
  }

  public void jumpTo(double angle, LatLng center, double pitch, double zoom) {
    if (isDestroyedOn("jumpTo")) {
      return;
    }
    nativeJumpTo(angle, center.getLatitude(), center.getLongitude(), pitch, zoom);
  }

  public void easeTo(double angle, LatLng center, long duration, double pitch, double zoom,
                     boolean easingInterpolator) {
    if (isDestroyedOn("easeTo")) {
      return;
    }
    nativeEaseTo(angle, center.getLatitude(), center.getLongitude(), duration, pitch, zoom,
      easingInterpolator);
  }

  public void flyTo(double angle, LatLng center, long duration, double pitch, double zoom) {
    if (isDestroyedOn("flyTo")) {
      return;
    }
    nativeFlyTo(angle, center.getLatitude(), center.getLongitude(), duration, pitch, zoom);
  }

  public double[] getCameraValues() {
    if (isDestroyedOn("getCameraValues")) {
      return new double[] {};
    }
    return nativeGetCameraValues();
  }

  // Runtime style Api

  public long getTransitionDuration() {
    return nativeGetTransitionDuration();
  }

  public void setTransitionDuration(long duration) {
    nativeSetTransitionDuration(duration);
  }

  public long getTransitionDelay() {
    return nativeGetTransitionDelay();
  }

  public void setTransitionDelay(long delay) {
    nativeSetTransitionDelay(delay);
  }

  public Layer getLayer(String layerId) {
    if (isDestroyedOn("getLayer")) {
      return null;
    }
    return nativeGetLayer(layerId);
  }

  public void addLayer(@NonNull Layer layer, @Nullable String before) {
    if (isDestroyedOn("")) {
      return;
    }
    nativeAddLayer(layer.getNativePtr(), before);
  }

  public void removeLayer(@NonNull String layerId) {
    if (isDestroyedOn("removeLayer")) {
      return;
    }
    nativeRemoveLayerById(layerId);
  }

  public void removeLayer(@NonNull Layer layer) {
    if (isDestroyedOn("removeLayer")) {
      return;
    }
    nativeRemoveLayer(layer.getNativePtr());
  }

  public Source getSource(@NonNull String sourceId) {
    if (isDestroyedOn("getSource")) {
      return null;
    }
    return nativeGetSource(sourceId);
  }

  public void addSource(@NonNull Source source) {
    if (isDestroyedOn("addSource")) {
      return;
    }
    nativeAddSource(source.getNativePtr());
  }

  public void removeSource(@NonNull String sourceId) {
    if (isDestroyedOn("removeSource")) {
      return;
    }
    nativeRemoveSourceById(sourceId);
  }

  public void removeSource(@NonNull Source source) {
    if (isDestroyedOn("removeSource")) {
      return;
    }
    nativeRemoveSource(source.getNativePtr());
  }

  public void addImage(@NonNull String name, @NonNull Bitmap image) {
    if (isDestroyedOn("addImage")) {
      return;
    }
    // Check/correct config
    if (image.getConfig() != Bitmap.Config.ARGB_8888) {
      image = image.copy(Bitmap.Config.ARGB_8888, false);
    }

    // Get pixels
    ByteBuffer buffer = ByteBuffer.allocate(image.getByteCount());
    image.copyPixelsToBuffer(buffer);

    // Determine pixel ratio
    float density = image.getDensity() == Bitmap.DENSITY_NONE ? Bitmap.DENSITY_NONE : image.getDensity();
    float pixelRatio = density / DisplayMetrics.DENSITY_DEFAULT;

    nativeAddImage(name, image.getWidth(), image.getHeight(), pixelRatio, buffer.array());
  }

  public void removeImage(String name) {
    if (isDestroyedOn("removeImage")) {
      return;
    }
    nativeRemoveImage(name);
  }

  // Feature querying

  @NonNull
  public List<Feature> queryRenderedFeatures(PointF coordinates, String... layerIds) {
    if (isDestroyedOn("queryRenderedFeatures")) {
      return new ArrayList<>();
    }
    Feature[] features = nativeQueryRenderedFeaturesForPoint(coordinates.x / pixelRatio,
      coordinates.y / pixelRatio, layerIds);
    return features != null ? Arrays.asList(features) : new ArrayList<Feature>();
  }

  @NonNull
  public List<Feature> queryRenderedFeatures(RectF coordinates, String... layerIds) {
    if (isDestroyedOn("queryRenderedFeatures")) {
      return new ArrayList<>();
    }
    Feature[] features = nativeQueryRenderedFeaturesForBox(

      coordinates.left / pixelRatio,
      coordinates.top / pixelRatio,
      coordinates.right / pixelRatio,
      coordinates.bottom / pixelRatio,
      layerIds);
    return features != null ? Arrays.asList(features) : new ArrayList<Feature>();
  }

  public void scheduleTakeSnapshot() {
    if (isDestroyedOn("scheduleTakeSnapshot")) {
      return;
    }
    nativeTakeSnapshot();
  }

  public void setApiBaseUrl(String baseUrl) {
    if (isDestroyedOn("setApiBaseUrl")) {
      return;
    }
    fileSource.setApiBaseUrl(baseUrl);
  }

  public float getPixelRatio() {
    return pixelRatio;
  }

  public Context getContext() {
    return mapView.getContext();
  }

  //
  // Callbacks
  //

  protected void onInvalidate() {
    if (mapView != null) {
      mapView.onInvalidate();
    }
  }

  protected void onMapChanged(int rawChange) {
    if (onMapChangedListeners != null) {
      for (MapView.OnMapChangedListener onMapChangedListener : onMapChangedListeners) {
        onMapChangedListener.onMapChanged(rawChange);
      }
    }
  }

  protected void onFpsChanged(double fps) {
    mapView.onFpsChanged(fps);
  }

  protected void onSnapshotReady(Bitmap bitmap) {
    if (snapshotReadyCallback != null && bitmap != null) {
      snapshotReadyCallback.onSnapshotReady(bitmap);
    }
  }

  //
  // JNI methods
  //

  private native void nativeInitialize(NativeMapView nativeMapView, FileSource fileSource,
                                       float pixelRatio, int availableProcessors, long totalMemory);

  private native void nativeDestroy();

  private native void nativeInitializeDisplay();

  private native void nativeTerminateDisplay();

  private native void nativeInitializeContext();

  private native void nativeTerminateContext();

  private native void nativeCreateSurface(Object surface);

  private native void nativeDestroySurface();

  private native void nativeUpdate();

  private native void nativeRender();

  private native void nativeResizeView(int width, int height);

  private native void nativeResizeFramebuffer(int fbWidth, int fbHeight);

  private native void nativeSetStyleUrl(String url);

  private native String nativeGetStyleUrl();

  private native void nativeSetStyleJson(String newStyleJson);

  private native String nativeGetStyleJson();

  private native void nativeCancelTransitions();

  private native void nativeSetGestureInProgress(boolean inProgress);

  private native void nativeMoveBy(double dx, double dy, long duration);

  private native void nativeSetLatLng(double latitude, double longitude, long duration);

  private native LatLng nativeGetLatLng();

  private native void nativeResetPosition();

  private native double nativeGetPitch();

  private native void nativeSetPitch(double pitch, long duration);

  private native void nativeScaleBy(double ds, double cx, double cy, long duration);

  private native void nativeSetScale(double scale, double cx, double cy, long duration);

  private native double nativeGetScale();

  private native void nativeSetZoom(double zoom, long duration);

  private native double nativeGetZoom();

  private native void nativeResetZoom();

  private native void nativeSetMinZoom(double zoom);

  private native double nativeGetMinZoom();

  private native void nativeSetMaxZoom(double zoom);

  private native double nativeGetMaxZoom();

  private native void nativeRotateBy(double sx, double sy, double ex, double ey, long duration);

  private native void nativeSetContentPadding(double top, double left, double bottom, double right);

  private native void nativeSetBearing(double degrees, long duration);

  private native void nativeSetBearingXY(double degrees, double fx, double fy, long duration);

  private native double nativeGetBearing();

  private native void nativeResetNorth();

  private native void nativeUpdateMarker(long markerId, double lat, double lon, String iconId);

  private native long[] nativeAddMarkers(Marker[] markers);

  private native long[] nativeAddPolylines(Polyline[] polylines);

  private native long[] nativeAddPolygons(Polygon[] polygons);

  private native void nativeRemoveAnnotations(long[] id);

  private native long[] nativeQueryPointAnnotations(RectF rect);

  private native void nativeAddAnnotationIcon(String symbol, int width, int height, float scale, byte[] pixels);

  private native void nativeSetVisibleCoordinateBounds(LatLng[] coordinates, RectF padding,
                                                       double direction, long duration);

  private native void nativeOnLowMemory();

  private native void nativeSetDebug(boolean debug);

  private native void nativeCycleDebugOptions();

  private native boolean nativeGetDebug();

  private native void nativeSetEnableFps(boolean enable);

  private native boolean nativeIsFullyLoaded();

  private native void nativeSetReachability(boolean status);

  private native double nativeGetMetersPerPixelAtLatitude(double lat, double zoom);

  private native ProjectedMeters nativeProjectedMetersForLatLng(double latitude, double longitude);

  private native LatLng nativeLatLngForProjectedMeters(double northing, double easting);

  private native PointF nativePixelForLatLng(double lat, double lon);

  private native LatLng nativeLatLngForPixel(float x, float y);

  private native double nativeGetTopOffsetPixelsForAnnotationSymbol(String symbolName);

  private native void nativeJumpTo(double angle, double latitude, double longitude, double pitch, double zoom);

  private native void nativeEaseTo(double angle, double latitude, double longitude,
                                   long duration, double pitch, double zoom,
                                   boolean easingInterpolator);

  private native void nativeFlyTo(double angle, double latitude, double longitude,
                                  long duration, double pitch, double zoom);

  private native double[] nativeGetCameraValues();

  private native long nativeGetTransitionDuration();

  private native void nativeSetTransitionDuration(long duration);

  private native long nativeGetTransitionDelay();

  private native void nativeSetTransitionDelay(long delay);

  private native Layer nativeGetLayer(String layerId);

  private native void nativeAddLayer(long layerPtr, String before) throws CannotAddLayerException;

  private native void nativeRemoveLayerById(String layerId);

  private native void nativeRemoveLayer(long layerId);

  private native Source nativeGetSource(String sourceId);

  private native void nativeAddSource(long nativeSourcePtr) throws CannotAddSourceException;

  private native void nativeRemoveSourceById(String sourceId);

  private native void nativeRemoveSource(long sourcePtr);

  private native void nativeAddImage(String name, int width, int height, float pixelRatio,
                                     byte[] array);

  private native void nativeRemoveImage(String name);

  private native void nativeUpdatePolygon(long polygonId, Polygon polygon);

  private native void nativeUpdatePolyline(long polylineId, Polyline polyline);

  private native void nativeTakeSnapshot();

  private native Feature[] nativeQueryRenderedFeaturesForPoint(float x, float y, String[]
    layerIds);

  private native Feature[] nativeQueryRenderedFeaturesForBox(float left, float top,
                                                             float right, float bottom,
                                                             String[] layerIds);

  int getWidth() {
    if (isDestroyedOn("")) {
      return 0;
    }
    return mapView.getWidth();
  }

  int getHeight() {
    if (isDestroyedOn("")) {
      return 0;
    }
    return mapView.getHeight();
  }

  //
  // MapChangeEvents
  //

  void addOnMapChangedListener(@NonNull MapView.OnMapChangedListener listener) {
    onMapChangedListeners.add(listener);
  }

  void removeOnMapChangedListener(@NonNull MapView.OnMapChangedListener listener) {
    onMapChangedListeners.remove(listener);
  }

  //
  // Snapshot
  //

  void addSnapshotCallback(@NonNull MapboxMap.SnapshotReadyCallback callback) {
    snapshotReadyCallback = callback;
    scheduleTakeSnapshot();
    render();
  }
}