package com.pichillilorenzo.flutter_inappwebview_android.webview.in_app_webview;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.pichillilorenzo.flutter_inappwebview_android.InAppWebViewFileProvider;
import com.pichillilorenzo.flutter_inappwebview_android.types.CreateWindowAction;
import com.pichillilorenzo.flutter_inappwebview_android.in_app_browser.ActivityResultListener;
import com.pichillilorenzo.flutter_inappwebview_android.in_app_browser.InAppBrowserDelegate;
import com.pichillilorenzo.flutter_inappwebview_android.InAppWebViewFlutterPlugin;
import com.pichillilorenzo.flutter_inappwebview_android.types.GeolocationPermissionShowPromptResponse;
import com.pichillilorenzo.flutter_inappwebview_android.types.JsAlertResponse;
import com.pichillilorenzo.flutter_inappwebview_android.types.JsBeforeUnloadResponse;
import com.pichillilorenzo.flutter_inappwebview_android.types.JsConfirmResponse;
import com.pichillilorenzo.flutter_inappwebview_android.types.JsPromptResponse;
import com.pichillilorenzo.flutter_inappwebview_android.types.PermissionResponse;
import com.pichillilorenzo.flutter_inappwebview_android.types.URLRequest;
import com.pichillilorenzo.flutter_inappwebview_android.webview.WebViewChannelDelegate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.PluginRegistry;

import static android.app.Activity.RESULT_OK;

public class InAppWebViewChromeClient extends WebChromeClient implements PluginRegistry.ActivityResultListener, ActivityResultListener {

  protected static final String LOG_TAG = "IABWebChromeClient";
  private InAppBrowserDelegate inAppBrowserDelegate;

  private static final int PICKER = 1;
  private static final int PICKER_LEGACY = 3;
  final String DEFAULT_MIME_TYPES = "*/*";
  final Map<DialogInterface, JsResult> dialogs = new HashMap();

  protected static final FrameLayout.LayoutParams FULLSCREEN_LAYOUT_PARAMS = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);

  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
  protected static final int FULLSCREEN_SYSTEM_UI_VISIBILITY_KITKAT = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
          View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
          View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
          View.SYSTEM_UI_FLAG_FULLSCREEN |
          View.SYSTEM_UI_FLAG_IMMERSIVE |
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

  protected static final int FULLSCREEN_SYSTEM_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
          View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
          View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
          View.SYSTEM_UI_FLAG_FULLSCREEN;

  @Nullable
  private View mCustomView;
  @Nullable
  private WebChromeClient.CustomViewCallback mCustomViewCallback;
  private int mOriginalOrientation;
  private int mOriginalSystemUiVisibility;
  @Nullable
  public InAppWebViewFlutterPlugin plugin;
  @Nullable
  public InAppWebView inAppWebView;

  @Nullable
  private ValueCallback<Uri> filePathCallbackLegacy;
  @Nullable
  private ValueCallback<Uri[]> filePathCallback;
  @Nullable
  private Uri videoOutputFileUri;
  @Nullable
  private Uri imageOutputFileUri;

//  private String cameraPhotoPath;

  public InAppWebViewChromeClient(@NonNull final InAppWebViewFlutterPlugin plugin,
                                  @NonNull InAppWebView inAppWebView, InAppBrowserDelegate inAppBrowserDelegate) {
    super();
    this.plugin = plugin;
    this.inAppWebView = inAppWebView;
    this.inAppBrowserDelegate = inAppBrowserDelegate;
    if (this.inAppBrowserDelegate != null) {
      this.inAppBrowserDelegate.getActivityResultListeners().add(this);
    }

    if (plugin.registrar != null)
      plugin.registrar.addActivityResultListener(this);
    else if (plugin.activityPluginBinding != null)
      plugin.activityPluginBinding.addActivityResultListener(this);
  }

  @Nullable
  @Override
  public Bitmap getDefaultVideoPoster() {
    if (inAppWebView != null && inAppWebView.customSettings.defaultVideoPoster != null) {
      final byte[] data = inAppWebView.customSettings.defaultVideoPoster;
      BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
      bitmapOptions.inMutable = true;
      return BitmapFactory.decodeByteArray(
              data, 0, data.length, bitmapOptions
      );
    }
    return Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
  }

  @Override
  public void onHideCustomView() {
    Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    View decorView = getRootView();
    if (decorView == null) {
      return;
    }
    if (this.mCustomView != null) {
      ((FrameLayout) decorView).removeView(this.mCustomView);
    }
    this.mCustomView = null;
    decorView.setSystemUiVisibility(this.mOriginalSystemUiVisibility);
    activity.setRequestedOrientation(this.mOriginalOrientation);
    if (this.mCustomViewCallback != null) {
      this.mCustomViewCallback.onCustomViewHidden();
    }
    this.mCustomViewCallback = null;
    activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

    if (inAppWebView != null) {
      WebViewChannelDelegate eventWebViewChannelDelegate = inAppWebView.channelDelegate;
      if (eventWebViewChannelDelegate != null)
        eventWebViewChannelDelegate.onExitFullscreen();
      inAppWebView.setInFullscreen(false);
    }
  }

  @Override
  public void onShowCustomView(final View paramView, final CustomViewCallback paramCustomViewCallback) {
    if (this.mCustomView != null) {
      onHideCustomView();
      return;
    }

    Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    View decorView = getRootView();
    if (decorView == null) {
      return;
    }
    this.mCustomView = paramView;
    this.mOriginalSystemUiVisibility = decorView.getSystemUiVisibility();
    this.mOriginalOrientation = activity.getRequestedOrientation();
    this.mCustomViewCallback = paramCustomViewCallback;
    if (this.mCustomView != null) {
      this.mCustomView.setBackgroundColor(Color.BLACK);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      decorView.setSystemUiVisibility(FULLSCREEN_SYSTEM_UI_VISIBILITY_KITKAT);
    } else {
      decorView.setSystemUiVisibility(FULLSCREEN_SYSTEM_UI_VISIBILITY);
    }
    activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    ((FrameLayout) decorView).addView(this.mCustomView, FULLSCREEN_LAYOUT_PARAMS);

    if (inAppWebView != null) {
      WebViewChannelDelegate eventWebViewChannelDelegate = inAppWebView.channelDelegate;
      if (eventWebViewChannelDelegate != null)
        eventWebViewChannelDelegate.onEnterFullscreen();
      inAppWebView.setInFullscreen(true);
    }
  }

  @Override
  public boolean onJsAlert(final WebView view, String url, final String message,
                           final JsResult result) {
    if (inAppWebView != null && inAppWebView.channelDelegate != null) {
      inAppWebView.channelDelegate.onJsAlert(url, message, null, new WebViewChannelDelegate.JsAlertCallback() {
        @Override
        public boolean nonNullSuccess(@NonNull JsAlertResponse response) {
          if (response.isHandledByClient()) {
            Integer action = response.getAction();
            action = action != null ? action : 1;
            switch (action) {
              case 0:
                result.confirm();
                break;
              case 1:
              default:
                result.cancel();
            }
            return false;
          }
          return true;
        }

        @Override
        public void defaultBehaviour(@Nullable JsAlertResponse response) {
          String responseMessage = null;
          String confirmButtonTitle = null;
          if (response != null) {
            responseMessage = response.getMessage();
            confirmButtonTitle = response.getConfirmButtonTitle();
          }
          createAlertDialog(message, result, responseMessage, confirmButtonTitle);
        }

        @Override
        public void error(String errorCode, @Nullable String errorMessage, @Nullable Object errorDetails) {
          Log.e(LOG_TAG, errorCode + ", " + ((errorMessage != null) ? errorMessage : ""));
          result.cancel();
        }
      });

      return true;
    }

    return false;
  }

  public void createAlertDialog(String message, final JsResult result, String responseMessage, String confirmButtonTitle) {
    String alertMessage = (responseMessage != null && !responseMessage.isEmpty()) ? responseMessage : message;

    DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        result.confirm();
        dialog.dismiss();
        dialogs.remove(dialog);
      }
    };

    Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert);
    alertDialogBuilder.setMessage(alertMessage);
    if (confirmButtonTitle != null && !confirmButtonTitle.isEmpty()) {
      alertDialogBuilder.setPositiveButton(confirmButtonTitle, clickListener);
    } else {
      alertDialogBuilder.setPositiveButton(android.R.string.ok, clickListener);
    }

    alertDialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        result.cancel();
        dialog.dismiss();
        dialogs.remove(dialog);
      }
    });

    AlertDialog alertDialog = alertDialogBuilder.create();
    dialogs.put(alertDialog, result);
    alertDialog.show();
  }

  @Override
  public boolean onJsConfirm(final WebView view, String url, final String message,
                             final JsResult result) {
    if (inAppWebView != null && inAppWebView.channelDelegate != null) {
      inAppWebView.channelDelegate.onJsConfirm(url, message, null, new WebViewChannelDelegate.JsConfirmCallback() {
        @Override
        public boolean nonNullSuccess(@NonNull JsConfirmResponse response) {
          if (response.isHandledByClient()) {
            Integer action = response.getAction();
            action = action != null ? action : 1;
            switch (action) {
              case 0:
                result.confirm();
                break;
              case 1:
              default:
                result.cancel();
            }
            return false;
          }
          return true;
        }

        @Override
        public void defaultBehaviour(@Nullable JsConfirmResponse response) {
          String responseMessage = null;
          String confirmButtonTitle = null;
          String cancelButtonTitle = null;
          if (response != null) {
            responseMessage = response.getMessage();
            confirmButtonTitle = response.getConfirmButtonTitle();
            cancelButtonTitle = response.getCancelButtonTitle();
          }
          createConfirmDialog(message, result, responseMessage, confirmButtonTitle, cancelButtonTitle);
        }

        @Override
        public void error(String errorCode, @Nullable String errorMessage, @Nullable Object errorDetails) {
          Log.e(LOG_TAG, errorCode + ", " + ((errorMessage != null) ? errorMessage : ""));
          result.cancel();
        }
      });

      return true;
    }

    return false;
  }

  public void createConfirmDialog(String message, final JsResult result, String responseMessage, String confirmButtonTitle, String cancelButtonTitle) {
    String alertMessage = (responseMessage != null && !responseMessage.isEmpty()) ? responseMessage : message;
    DialogInterface.OnClickListener confirmClickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        result.confirm();
        dialog.dismiss();
        dialogs.remove(dialog);
      }
    };
    DialogInterface.OnClickListener cancelClickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        result.cancel();
        dialog.dismiss();
        dialogs.remove(dialog);
      }
    };

    Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert);
    alertDialogBuilder.setMessage(alertMessage);
    if (confirmButtonTitle != null && !confirmButtonTitle.isEmpty()) {
      alertDialogBuilder.setPositiveButton(confirmButtonTitle, confirmClickListener);
    } else {
      alertDialogBuilder.setPositiveButton(android.R.string.ok, confirmClickListener);
    }
    if (cancelButtonTitle != null && !cancelButtonTitle.isEmpty()) {
      alertDialogBuilder.setNegativeButton(cancelButtonTitle, cancelClickListener);
    } else {
      alertDialogBuilder.setNegativeButton(android.R.string.cancel, cancelClickListener);
    }

    alertDialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        result.cancel();
        dialog.dismiss();
        dialogs.remove(dialog);
      }
    });

    AlertDialog alertDialog = alertDialogBuilder.create();
    dialogs.put(alertDialog, result);
    alertDialog.show();
  }

  @Override
  public boolean onJsPrompt(final WebView view, String url, final String message,
                            final String defaultValue, final JsPromptResult result) {
    if (inAppWebView != null && inAppWebView.channelDelegate != null) {
      inAppWebView.channelDelegate.onJsPrompt(url, message, defaultValue, null, new WebViewChannelDelegate.JsPromptCallback() {
        @Override
        public boolean nonNullSuccess(@NonNull JsPromptResponse response) {
          if (response.isHandledByClient()) {
            Integer action = response.getAction();
            action = action != null ? action : 1;
            switch (action) {
              case 0:
                result.confirm(response.getValue());
                break;
              case 1:
              default:
                result.cancel();
            }
            return false;
          }
          return true;
        }

        @Override
        public void defaultBehaviour(@Nullable JsPromptResponse response) {
          String responseMessage = null;
          String responseDefaultValue = null;
          String value = null;
          String confirmButtonTitle = null;
          String cancelButtonTitle = null;
          if (response != null) {
            responseMessage = response.getMessage();
            responseDefaultValue = response.getDefaultValue();
            value = response.getValue();
            confirmButtonTitle = response.getConfirmButtonTitle();
            cancelButtonTitle = response.getCancelButtonTitle();
          }
          createPromptDialog(view, message, defaultValue, result, responseMessage, responseDefaultValue, value, cancelButtonTitle, confirmButtonTitle);
        }

        @Override
        public void error(String errorCode, @Nullable String errorMessage, @Nullable Object errorDetails) {
          Log.e(LOG_TAG, errorCode + ", " + ((errorMessage != null) ? errorMessage : ""));
          result.cancel();
        }
      });

      return true;
    }

    return false;
  }

  public void createPromptDialog(WebView view, String message, String defaultValue, final JsPromptResult result, String responseMessage, String responseDefaultValue, String value, String cancelButtonTitle, String confirmButtonTitle) {
    FrameLayout layout = new FrameLayout(view.getContext());

    final EditText input = new EditText(view.getContext());
    input.setMaxLines(1);
    input.setText((responseDefaultValue != null && !responseDefaultValue.isEmpty()) ? responseDefaultValue : defaultValue);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT);
    input.setLayoutParams(lp);

    layout.setPaddingRelative(45, 15, 45, 0);
    layout.addView(input);

    String alertMessage = (responseMessage != null && !responseMessage.isEmpty()) ? responseMessage : message;

    final String finalValue = value;
    DialogInterface.OnClickListener confirmClickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        String text = input.getText().toString();
        result.confirm(finalValue != null ? finalValue : text);
        dialog.dismiss();
        dialogs.remove(dialog);
      }
    };
    DialogInterface.OnClickListener cancelClickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        result.cancel();
        dialog.dismiss();
        dialogs.remove(dialog);
      }
    };

    Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert);
    alertDialogBuilder.setMessage(alertMessage);
    if (confirmButtonTitle != null && !confirmButtonTitle.isEmpty()) {
      alertDialogBuilder.setPositiveButton(confirmButtonTitle, confirmClickListener);
    } else {
      alertDialogBuilder.setPositiveButton(android.R.string.ok, confirmClickListener);
    }
    if (cancelButtonTitle != null && !cancelButtonTitle.isEmpty()) {
      alertDialogBuilder.setNegativeButton(cancelButtonTitle, cancelClickListener);
    } else {
      alertDialogBuilder.setNegativeButton(android.R.string.cancel, cancelClickListener);
    }

    alertDialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        result.cancel();
        dialog.dismiss();
        dialogs.remove(dialog);
      }
    });

    AlertDialog alertDialog = alertDialogBuilder.create();
    alertDialog.setView(layout);
    dialogs.put(alertDialog, result);
    alertDialog.show();
  }

  @Override
  public boolean onJsBeforeUnload(final WebView view, String url, final String message,
                                  final JsResult result) {
    if (inAppWebView != null && inAppWebView.channelDelegate != null) {
      inAppWebView.channelDelegate.onJsBeforeUnload(url, message, new WebViewChannelDelegate.JsBeforeUnloadCallback() {
        @Override
        public boolean nonNullSuccess(@NonNull JsBeforeUnloadResponse response) {
          if (response.isHandledByClient()) {
            Integer action = response.getAction();
            action = action != null ? action : 1;
            switch (action) {
              case 0:
                result.confirm();
                break;
              case 1:
              default:
                result.cancel();
            }
            return false;
          }
          return true;
        }

        @Override
        public void defaultBehaviour(@Nullable JsBeforeUnloadResponse response) {
          String responseMessage = null;
          String confirmButtonTitle = null;
          String cancelButtonTitle = null;
          if (response != null) {
            responseMessage = response.getMessage();
            confirmButtonTitle = response.getConfirmButtonTitle();
            cancelButtonTitle = response.getCancelButtonTitle();
          }
          createBeforeUnloadDialog(message, result, responseMessage, confirmButtonTitle, cancelButtonTitle);
        }

        @Override
        public void error(String errorCode, @Nullable String errorMessage, @Nullable Object errorDetails) {
          Log.e(LOG_TAG, errorCode + ", " + ((errorMessage != null) ? errorMessage : ""));
          result.cancel();
        }
      });

      return true;
    }

    return false;
  }

  public void createBeforeUnloadDialog(String message, final JsResult result, String responseMessage, String confirmButtonTitle, String cancelButtonTitle) {
    String alertMessage = (responseMessage != null && !responseMessage.isEmpty()) ? responseMessage : message;
    DialogInterface.OnClickListener confirmClickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        result.confirm();
        dialog.dismiss();
        dialogs.remove(dialog);
      }
    };
    DialogInterface.OnClickListener cancelClickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        result.cancel();
        dialog.dismiss();
        dialogs.remove(dialog);
      }
    };

    Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert);
    alertDialogBuilder.setMessage(alertMessage);
    if (confirmButtonTitle != null && !confirmButtonTitle.isEmpty()) {
      alertDialogBuilder.setPositiveButton(confirmButtonTitle, confirmClickListener);
    } else {
      alertDialogBuilder.setPositiveButton(android.R.string.ok, confirmClickListener);
    }
    if (cancelButtonTitle != null && !cancelButtonTitle.isEmpty()) {
      alertDialogBuilder.setNegativeButton(cancelButtonTitle, cancelClickListener);
    } else {
      alertDialogBuilder.setNegativeButton(android.R.string.cancel, cancelClickListener);
    }

    alertDialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        result.cancel();
        dialog.dismiss();
        dialogs.remove(dialog);
      }
    });

    AlertDialog alertDialog = alertDialogBuilder.create();
    dialogs.put(alertDialog, result);
    alertDialog.show();
  }

  @Override
  public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, final Message resultMsg) {
    int windowId = 0;
    if (plugin != null && plugin.inAppWebViewManager != null) {
      plugin.inAppWebViewManager.windowAutoincrementId++;
      windowId = plugin.inAppWebViewManager.windowAutoincrementId;
    }

    WebView.HitTestResult result = view.getHitTestResult();
    String url = result.getExtra();

    // Ensure that images with hyperlink return the correct URL, not the image source
    if(result.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
      Message href = view.getHandler().obtainMessage();
      view.requestFocusNodeHref(href);
      Bundle data = href.getData();
      if (data != null) {
        String imageUrl = data.getString("url");
        if(imageUrl != null && !imageUrl.isEmpty()) {
          url = imageUrl;
        }
      }
    }

    URLRequest request = new URLRequest(url, "GET", null, null);
    CreateWindowAction createWindowAction = new CreateWindowAction(
            request,
            true,
            isUserGesture,
            false,
            windowId,
            isDialog
    );

    if (plugin != null && plugin.inAppWebViewManager != null) {
      plugin.inAppWebViewManager.windowWebViewMessages.put(windowId, resultMsg);
    }

    if (inAppWebView != null && inAppWebView.channelDelegate != null) {
      final int finalWindowId = windowId;
      inAppWebView.channelDelegate.onCreateWindow(createWindowAction, new WebViewChannelDelegate.CreateWindowCallback() {
        @Override
        public boolean nonNullSuccess(@NonNull Boolean handledByClient) {
          return !handledByClient;
        }

        @Override
        public void defaultBehaviour(@Nullable Boolean handledByClient) {
          if (plugin != null && plugin.inAppWebViewManager != null) {
            plugin.inAppWebViewManager.windowWebViewMessages.remove(finalWindowId);
          }
        }

        @Override
        public void error(String errorCode, @Nullable String errorMessage, @Nullable Object errorDetails) {
          Log.e(LOG_TAG, errorCode + ", " + ((errorMessage != null) ? errorMessage : ""));
          defaultBehaviour(null);
        }
      });

      return true;
    }

    return false;
  }

  @Override
  public void onCloseWindow(WebView window) {
    if (inAppWebView != null && inAppWebView.channelDelegate != null) {
      inAppWebView.channelDelegate.onCloseWindow();
    }

    super.onCloseWindow(window);
  }

  @Override
  public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
    final WebViewChannelDelegate.GeolocationPermissionsShowPromptCallback resultCallback = new WebViewChannelDelegate.GeolocationPermissionsShowPromptCallback() {
      @Override
      public boolean nonNullSuccess(@NonNull GeolocationPermissionShowPromptResponse response) {
        callback.invoke(response.getOrigin(), response.isAllow(), response.isRetain());
        return false;
      }

      @Override
      public void defaultBehaviour(@Nullable GeolocationPermissionShowPromptResponse response) {
        callback.invoke(origin, false, false);
      }

      @Override
      public void error(String errorCode, @Nullable String errorMessage, @Nullable Object errorDetails) {
        Log.e(LOG_TAG, errorCode + ", " + ((errorMessage != null) ? errorMessage : ""));
        defaultBehaviour(null);
      }
    };

    if (inAppWebView != null && inAppWebView.channelDelegate != null) {
      inAppWebView.channelDelegate.onGeolocationPermissionsShowPrompt(origin, resultCallback);
    } else {
      resultCallback.defaultBehaviour(null);
    }
  }

  @Override
  public void onGeolocationPermissionsHidePrompt() {
    if (inAppWebView != null && inAppWebView.channelDelegate != null) {
      inAppWebView.channelDelegate.onGeolocationPermissionsHidePrompt();
    }
  }

  @Override
  public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
    if (inAppWebView != null && inAppWebView.channelDelegate != null) {
      inAppWebView.channelDelegate.onConsoleMessage(
              consoleMessage.message(),
              consoleMessage.messageLevel().ordinal());
    }
    return true;
  }

  @Override
  public void onProgressChanged(WebView view, int progress) {
    super.onProgressChanged(view, progress);

    if (inAppBrowserDelegate != null) {
      inAppBrowserDelegate.didChangeProgress(progress);
    }


    InAppWebView webView = (InAppWebView) view;

    if (webView.inAppWebViewClientCompat != null) {
      webView.inAppWebViewClientCompat.loadCustomJavaScriptOnPageStarted(view);
    } else if (webView.inAppWebViewClient != null) {
      webView.inAppWebViewClient.loadCustomJavaScriptOnPageStarted(view);
    }

    if (webView.channelDelegate != null) {
      webView.channelDelegate.onProgressChanged(progress);
    }
  }

  @Override
  public void onReceivedTitle(WebView view, String title) {
    super.onReceivedTitle(view, title);

    if (inAppBrowserDelegate != null) {
      inAppBrowserDelegate.didChangeTitle(title);
    }

    InAppWebView webView = (InAppWebView) view;

    if (webView.channelDelegate != null) {
      webView.channelDelegate.onTitleChanged(title);
    }
  }

  @Override
  public void onReceivedIcon(WebView view, Bitmap icon) {
    super.onReceivedIcon(view, icon);

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    icon.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
    try {
      byteArrayOutputStream.close();
    } catch (IOException e) {
      Log.e(LOG_TAG, "", e);
    }
    icon.recycle();

    InAppWebView webView = (InAppWebView) view;
    if (webView.channelDelegate != null) {
      webView.channelDelegate.onReceivedIcon(byteArrayOutputStream.toByteArray());
    }
  }

  @Override
  public void onReceivedTouchIconUrl(WebView view,
                                     String url,
                                     boolean precomposed) {
    super.onReceivedTouchIconUrl(view, url, precomposed);

    InAppWebView webView = (InAppWebView) view;
    if (webView.channelDelegate != null) {
      webView.channelDelegate.onReceivedTouchIconUrl(url, precomposed);
    }
  }

  @Nullable
  protected ViewGroup getRootView() {
    Activity activity = getActivity();
    if (activity == null) {
      return null;
    }
    return (ViewGroup) activity.findViewById(android.R.id.content);
  }

  protected void openFileChooser(ValueCallback<Uri> filePathCallback, String acceptType) {
    startPickerIntent(filePathCallback, acceptType, null);
  }

  protected void openFileChooser(ValueCallback<Uri> filePathCallback) {
    startPickerIntent(filePathCallback, "", null);
  }

  protected void openFileChooser(ValueCallback<Uri> filePathCallback, String acceptType, String capture) {
    startPickerIntent(filePathCallback, acceptType, capture);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
    String[] acceptTypes = fileChooserParams.getAcceptTypes();
    boolean allowMultiple = fileChooserParams.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
    boolean captureEnabled = fileChooserParams.isCaptureEnabled();
    return startPickerIntent(filePathCallback, acceptTypes, allowMultiple, captureEnabled);
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
    if (filePathCallback == null && filePathCallbackLegacy == null) {
      return true;
    }


    Uri[] results = null;
    // based off of which button was pressed, we get an activity result and a file
    // the camera activity doesn't properly return the filename* (I think?) so we use
    // this filename instead
    switch (requestCode) {
      case PICKER:
        if (resultCode == RESULT_OK) {
          results = getSelectedFiles(data, resultCode);
        }

        if (filePathCallback != null) {
          filePathCallback.onReceiveValue(results);
        }
        break;

      case PICKER_LEGACY:
        Uri result = null;
        if (resultCode == RESULT_OK) {
          String filePath = "";
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            filePath = data.getDataString();
            results = getSelectedFiles(data, resultCode);
          }
//          else {
//            filePath = "file:" + getRealPath(getActivity(), data.getData());
//          }

          if (filePath.isEmpty()) {
            result = data.getData();
          }
          else {
            result = Uri.parse(filePath);
          }
        }

        final Uri handleResult = result;

        if (filePathCallback != null) {
          filePathCallback.onReceiveValue(results);
        }
        if (filePathCallbackLegacy != null) {
          filePathCallbackLegacy.onReceiveValue(handleResult);
        }

//        new Handler().postDelayed(new Runnable() {
//          @Override
//          public void run() {
//            if (filePathCallbackLegacy != null) {
//              filePathCallbackLegacy.onReceiveValue(handleResult);
//            }
//          }
//        }, 500); // 2000 is the delay in milliseconds (2 seconds)

        break;
    }

    filePathCallback = null;
    filePathCallbackLegacy = null;
    imageOutputFileUri = null;
    videoOutputFileUri = null;

    return true;
  }

  private boolean isExternalStorageDocument(Uri uri) {
    return "com.android.externalstorage.documents".equals(uri.getAuthority());
  }

  private boolean isDownloadsDocument(Uri uri) {
    return "com.android.providers.downloads.documents".equals(uri.getAuthority());
  }


  private boolean isMediaDocument(Uri uri) {
    return "com.android.providers.media.documents".equals(uri.getAuthority());
  }

  private boolean isGooglePhotosUri(Uri uri) {
    return "com.google.android.apps.photos.content".equals(uri.getAuthority());
  }

  private String copyFileToInternalStorage(Context context, Uri uri, String newDirName) {
    Uri returnUri = uri;
    Cursor returnCursor = context.getContentResolver().query(
            returnUri,
            new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
            null,
            null,
            null
    );
    int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
    returnCursor.moveToFirst();
    String name = returnCursor.getString(nameIndex);

    File output;
    if (!newDirName.equals("")) {
      File dir = new File(context.getFilesDir().toString() + "/" + newDirName);
      if (!dir.exists()) {
        dir.mkdir();
      }
      output = new File(context.getFilesDir().toString() + "/" + newDirName + "/" + name);
    } else {
      output = new File(context.getFilesDir().toString() + "/" + name);
    }

    try {
      InputStream inputStream = context.getContentResolver().openInputStream(uri);
      if (inputStream != null) {
        FileOutputStream outputStream = new FileOutputStream(output);
        int read;
        int bufferSize = 1024;
        byte[] buffers = new byte[bufferSize];
        while ((read = inputStream.read(buffers)) != -1) {
          outputStream.write(buffers, 0, read);
        }
        inputStream.close();
        outputStream.close();
      }
      return output.getPath();
    } catch (Exception e) {
      // Handle the exception
    }

    return null;
  }

  private String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
    // function body
    Cursor cursor = null;
    String column = "_data";
    String[] projection = {column};
    try {
      cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
      if (cursor != null && cursor.moveToFirst()) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.getString(index);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    finally {
      cursor.close();
    }
    return null;
  }

  private String getRealPath(Context context, Uri uri) {
    // check here to KITKAT or new version
    boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

    // DocumentProvider
    if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
      // ExternalStorageProvider
      if (isExternalStorageDocument(uri)) {
        String docId = DocumentsContract.getDocumentId(uri);
        String[] split = docId.split(":");
        String type = split[0];

        if ("primary".equalsIgnoreCase(type)) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyFileToInternalStorage(context, uri, "gjfiles");
          } else {
            return Environment.getExternalStorageDirectory() + "/" + split[1];
          }
        }
      } else if (isDownloadsDocument(uri)) {
        String id = DocumentsContract.getDocumentId(uri);
        Uri contentUri = ContentUris.withAppendedId(
                Uri.parse("content://downloads/public_downloads"),
                Long.valueOf(id)
        );

        return getDataColumn(context, contentUri, null, null);
      } else if (isMediaDocument(uri)) {
        String docId = DocumentsContract.getDocumentId(uri);
        String[] split = docId.split(":");
        String type = split[0];

        Uri contentUri = null;
        if ("image".equals(type)) {
          contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else if ("video".equals(type)) {
          contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else if ("audio".equals(type)) {
          contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        String selection = "_id=?";
        String[] selectionArgs = new String[]{split[1]};

        return getDataColumn(context, contentUri, selection, selectionArgs);
      }
    } else if ("content".equalsIgnoreCase(uri.getScheme())) {
      // Return the remote address
      if (isGooglePhotosUri(uri)) {
        return uri.getLastPathSegment();
      } else {
        return getDataColumn(context, uri, null, null);
      }
    } else if ("file".equalsIgnoreCase(uri.getScheme())) {
      return uri.getPath();
    }

    return null;
  }

  private Uri[] getSelectedFiles(Intent data, int resultCode) {
    // we have one file selected
    if (data != null && data.getData() != null) {
      if (resultCode == RESULT_OK && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        return WebChromeClient.FileChooserParams.parseResult(resultCode, data);
      } else {
        return null;
      }
    }

    // we have multiple files selected
    if (data != null && data.getClipData() != null) {
      final int numSelectedFiles = data.getClipData().getItemCount();
      Uri[] result = new Uri[numSelectedFiles];
      for (int i = 0; i < numSelectedFiles; i++) {
        result[i] = data.getClipData().getItemAt(i).getUri();
      }
      return result;
    }

    // we have a captured image or video file
    Uri mediaUri = getCapturedMediaFile();
    if (mediaUri != null) {
      return new Uri[]{mediaUri};
    }

    return null;
  }

  private boolean isFileNotEmpty(Uri uri) {
    Activity activity = getActivity();
    if (activity == null) {
      return false;
    }

    long length;
    try {
      AssetFileDescriptor descriptor = activity.getContentResolver().openAssetFileDescriptor(uri, "r");
      length = descriptor.getLength();
      descriptor.close();
    } catch (IOException e) {
      return false;
    }

    return length > 0;
  }

  private Uri getCapturedMediaFile() {
    if (imageOutputFileUri != null && isFileNotEmpty(imageOutputFileUri)) {
      return imageOutputFileUri;
    }

    if (videoOutputFileUri != null && isFileNotEmpty(videoOutputFileUri)) {
      return videoOutputFileUri;
    }

    return null;
  }

  public void startPickerIntent(ValueCallback<Uri> filePathCallback, String acceptType, @Nullable String capture) {
    filePathCallbackLegacy = filePathCallback;

    boolean images = acceptsImages(acceptType);
    boolean video = acceptsVideo(acceptType);

    // 피커 인텐트 선언
    Intent pickerIntent = null;

    // capture 가 들어오는 경로는 확인하려면 더 파고 들어가야 하는데.. 비디오 캡쳐는 사용할 일이 없긴하다.
    // image 캡쳐가 들어온다고 가정
    if (capture != null) {
      if (!needsCameraPermission()) {
        if (images) {
          // 들어오는 인텐트는 chooser intent 안에 포토 캡쳐 가능하게 수정
          pickerIntent = getPhotoIntent();
          // getPhotoIntent 에서 파일 이름 만들어서 날리게 수정 하였음.
        }
        else if (video) {
          pickerIntent = getVideoIntent();
        }
      }
    }
    // 여기서 이미지 피커 null 이면 파일 추져를 날린다.
    if (pickerIntent == null) {
      Intent fileChooserIntent = getFileChooserIntent(acceptType);
      pickerIntent = Intent.createChooser(fileChooserIntent, "");

      ArrayList<Parcelable> extraIntents = new ArrayList<>();
      if (!needsCameraPermission()) {
        if (images) {
          extraIntents.add(getPhotoIntent());
        }
        if (video) {
          extraIntents.add(getVideoIntent());
        }
      }
      pickerIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toArray(new Parcelable[]{}));
    }

    Activity activity = getActivity();
    if (activity != null && pickerIntent.resolveActivity(activity.getPackageManager()) != null) {
      activity.startActivityForResult(pickerIntent, PICKER_LEGACY);
    } else {
      Log.d(LOG_TAG, "there is no Activity to handle this Intent");
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public boolean startPickerIntent(final ValueCallback<Uri[]> callback, final String[] acceptTypes,
                                   final boolean allowMultiple, final boolean captureEnabled) {
    filePathCallback = callback;

    boolean images = acceptsImages(acceptTypes);
    boolean video = acceptsVideo(acceptTypes);

    Intent pickerIntent = null;

    if (captureEnabled) {
      if (!needsCameraPermission()) {
        if (images) {
          pickerIntent = getPhotoIntent();
        }
        else if (video) {
          pickerIntent = getVideoIntent();
        }
      }
    }
    if (pickerIntent == null) {
      ArrayList<Parcelable> extraIntents = new ArrayList<>();
      if (!needsCameraPermission()) {
        if (images) {
          extraIntents.add(getPhotoIntent());
        }
        if (video) {
          extraIntents.add(getVideoIntent());
        }
      }

      Intent fileSelectionIntent = getFileChooserIntent(acceptTypes, allowMultiple);

      pickerIntent = new Intent(Intent.ACTION_CHOOSER);
      pickerIntent.putExtra(Intent.EXTRA_INTENT, fileSelectionIntent);
      pickerIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toArray(new Parcelable[]{}));
    }

    Activity activity = getActivity();
    if (activity != null && pickerIntent.resolveActivity(activity.getPackageManager()) != null) {
      activity.startActivityForResult(pickerIntent, PICKER);
    } else {
      Log.d(LOG_TAG, "there is no Activity to handle this Intent");
    }

    return true;
  }

  protected boolean needsCameraPermission() {
    boolean needed = false;

    Activity activity = getActivity();
    if (activity == null) {
      return true;
    }
    PackageManager packageManager = activity.getPackageManager();
    try {
      String[] requestedPermissions = packageManager.getPackageInfo(activity.getApplicationContext().getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
      if (Arrays.asList(requestedPermissions).contains(Manifest.permission.CAMERA)
              && ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        needed = true;
      }
    } catch (PackageManager.NameNotFoundException e) {
      needed = true;
    }

    return needed;
  }


  private static final int INPUT_FILE_REQUEST_CODE = 2;

  private String TYPE_IMAGE = "image/*";

  private Intent getPhotoIntent() {
    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    imageOutputFileUri = getOutputUri(MediaStore.ACTION_IMAGE_CAPTURE);
    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageOutputFileUri);

    Intent contentSelectionIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
//    Intent contentSelectionIntent = new Intent(MediaStore.ACTION_PICK_IMAGES);
    contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
    contentSelectionIntent.setType("image/*"); // Assuming TYPE_IMAGE is a constant with value "image/*"

    Intent[] intentArray;
    if (takePictureIntent != null) {
      intentArray = new Intent[]{takePictureIntent};
    }
    else {
      intentArray = new Intent[0];
    }

    Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
    chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

//    getActivity().startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);

//    intent.putExtra(MediaStore.EXTRA_OUTPUT, imageOutputFileUri);
    return chooserIntent;
  }

  private Intent getVideoIntent() {
    Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
    videoOutputFileUri = getOutputUri(MediaStore.ACTION_VIDEO_CAPTURE);
    intent.putExtra(MediaStore.EXTRA_OUTPUT, videoOutputFileUri);
    return intent;
  }

  private Intent getFileChooserIntent(String acceptTypes) {
    String _acceptTypes = acceptTypes;
    if (acceptTypes.isEmpty()) {
      _acceptTypes = DEFAULT_MIME_TYPES;
    }
    if (acceptTypes.matches("\\.\\w+")) {
      _acceptTypes = getMimeTypeFromExtension(acceptTypes.replace(".", ""));
    }
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType(_acceptTypes);
    return intent;
  }

  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
  private Intent getFileChooserIntent(String[] acceptTypes, boolean allowMultiple) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    intent.putExtra(Intent.EXTRA_MIME_TYPES, getAcceptedMimeType(acceptTypes));
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
    return intent;
  }

  private Boolean acceptsAny(String[] types) {
    if (isArrayEmpty(types)) {
      return true;
    }

    for (String type : types) {
      if (type.equals("*/*")) {
        return true;
      }
    }

    return false;
  }

  private Boolean acceptsImages(String types) {
    String mimeType = types;
    if (types.matches("\\.\\w+")) {
      mimeType = getMimeTypeFromExtension(types.replace(".", ""));
    }
    return mimeType.isEmpty() || mimeType.toLowerCase().contains("image");
  }

  private Boolean acceptsImages(String[] types) {
    String[] mimeTypes = getAcceptedMimeType(types);
    return acceptsAny(types) || arrayContainsString(mimeTypes, "image");
  }

  private Boolean acceptsVideo(String types) {
    String mimeType = types;
    if (types.matches("\\.\\w+")) {
      mimeType = getMimeTypeFromExtension(types.replace(".", ""));
    }
    return mimeType.isEmpty() || mimeType.toLowerCase().contains("video");
  }

  private Boolean acceptsVideo(String[] types) {
    String[] mimeTypes = getAcceptedMimeType(types);
    return acceptsAny(types) || arrayContainsString(mimeTypes, "video");
  }

  private Boolean arrayContainsString(String[] array, String pattern) {
    for (String content : array) {
      if (content != null && content.contains(pattern)) {
        return true;
      }
    }
    return false;
  }

  private String[] getAcceptedMimeType(String[] types) {
    if (isArrayEmpty(types)) {
      return new String[]{DEFAULT_MIME_TYPES};
    }
    String[] mimeTypes = new String[types.length];
    for (int i = 0; i < types.length; i++) {
      String t = types[i];
      // convert file extensions to mime types
      if (t.matches("\\.\\w+")) {
        String mimeType = getMimeTypeFromExtension(t.replace(".", ""));
        mimeTypes[i] = mimeType;
      } else {
        mimeTypes[i] = t;
      }
    }
    return mimeTypes;
  }

  private String getMimeTypeFromExtension(String extension) {
    String type = null;
    if (extension != null) {
      type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }
    return type;
  }

  @Nullable
  private Uri getOutputUri(String intentType) {
    File capturedFile = null;
    try {
      capturedFile = getCapturedFile(intentType);
    } catch (IOException e) {
      Log.e(LOG_TAG, "Error occurred while creating the File", e);
    }
    if (capturedFile == null) {
      return null;
    }

    // for versions below 6.0 (23) we use the old File creation & permissions model
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return Uri.fromFile(capturedFile);
    }

    Activity activity = getActivity();
    if (activity == null) {
      return null;
    }
    // for versions 6.0+ (23) we use the FileProvider to avoid runtime permissions
    String fileProviderAuthority = activity.getApplicationContext().getPackageName() + "." +
            InAppWebViewFileProvider.fileProviderAuthorityExtension;
    try {
      return FileProvider.getUriForFile(activity.getApplicationContext(),
              fileProviderAuthority,
              capturedFile);
    } catch (Exception e) {
      Log.e(LOG_TAG, "", e);
    }
    return null;
  }

  @Nullable
  private File getCapturedFile(String intentType) throws IOException {
    String prefix = "";
    String suffix = "";
    String dir = "";

    if (intentType.equals(MediaStore.ACTION_IMAGE_CAPTURE)) {
      prefix = "image";
      suffix = ".jpg";
      dir = Environment.DIRECTORY_PICTURES;
    } else if (intentType.equals(MediaStore.ACTION_VIDEO_CAPTURE)) {
      prefix = "video";
      suffix = ".mp4";
      dir = Environment.DIRECTORY_MOVIES;
    }

    // for versions below 6.0 (23) we use the old File creation & permissions model
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      // only this Directory works on all tested Android versions
      // ctx.getExternalFilesDir(dir) was failing on Android 5.0 (sdk 21)
      File storageDir = Environment.getExternalStoragePublicDirectory(dir);
      String filename = String.format("%s-%d%s", prefix, System.currentTimeMillis(), suffix);
      return new File(storageDir, filename);
    }

    Activity activity = getActivity();
    if (activity == null) {
      return null;
    }
    File storageDir = activity.getApplicationContext().getExternalFilesDir(null);
    return File.createTempFile(prefix, suffix, storageDir);
  }

  private Boolean isArrayEmpty(String[] arr) {
    // when our array returned from getAcceptTypes() has no values set from the webview
    // i.e. <input type="file" />, without any "accept" attr
    // will be an array with one empty string element, afaik
    return arr.length == 0 || (arr.length == 1 && arr[0].length() == 0);
  }

  @Override
  public void onPermissionRequest(final PermissionRequest request) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      final WebViewChannelDelegate.PermissionRequestCallback callback = new WebViewChannelDelegate.PermissionRequestCallback() {
        @Override
        public boolean nonNullSuccess(@NonNull PermissionResponse response) {
          Integer action = response.getAction();
          if (action != null) {
            switch (action) {
              case 1:
                String[] resources = new String[response.getResources().size()];
                resources = response.getResources().toArray(resources);
                request.grant(resources);
                break;
              case 0:
              default:
                request.deny();
            }
            return false;
          }
          return true;
        }

        @Override
        public void defaultBehaviour(@Nullable PermissionResponse response) {
          request.deny();
        }

        @Override
        public void error(String errorCode, @Nullable String errorMessage, @Nullable Object errorDetails) {
          Log.e(LOG_TAG, errorCode + ", " + ((errorMessage != null) ? errorMessage : ""));
          defaultBehaviour(null);
        }
      };

      if(inAppWebView != null && inAppWebView.channelDelegate != null) {
        inAppWebView.channelDelegate.onPermissionRequest(request.getOrigin().toString(),
                Arrays.asList(request.getResources()), null, callback);
      } else {
        callback.defaultBehaviour(null);
      }
    }
  }

  @Override
  public void onRequestFocus(WebView view) {
    if(inAppWebView != null && inAppWebView.channelDelegate != null) {
      inAppWebView.channelDelegate.onRequestFocus();
    }
  }

  @Override
  public void onPermissionRequestCanceled(PermissionRequest request) {
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
            inAppWebView != null && inAppWebView.channelDelegate != null) {
      inAppWebView.channelDelegate.onPermissionRequestCanceled(request.getOrigin().toString(),
              Arrays.asList(request.getResources()));
    }
  }

  @Nullable
  private Activity getActivity() {
    if (inAppBrowserDelegate != null) {
      return inAppBrowserDelegate.getActivity();
    } else if (plugin != null) {
      return plugin.activity;
    }
    return null;
  }

  public void dispose() {
    for (Map.Entry<DialogInterface, JsResult> dialog : dialogs.entrySet()) {
      dialog.getValue().cancel();
      dialog.getKey().dismiss();
    }
    dialogs.clear();
    if (plugin != null && plugin.activityPluginBinding != null) {
      plugin.activityPluginBinding.removeActivityResultListener(this);
    }
    if (inAppBrowserDelegate != null) {
      inAppBrowserDelegate.getActivityResultListeners().clear();
      inAppBrowserDelegate = null;
    }
    filePathCallbackLegacy = null;
    filePathCallback = null;
    videoOutputFileUri = null;
    imageOutputFileUri = null;
    inAppWebView = null;
    plugin = null;
  }
}