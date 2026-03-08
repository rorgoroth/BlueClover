/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.otacoo.chan.ui.view;

import static org.otacoo.chan.Chan.inject;
import static org.otacoo.chan.Chan.injector;
import static org.otacoo.chan.utils.AndroidUtils.dp;

import android.content.ClipData;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.otacoo.chan.R;
import org.otacoo.chan.core.cache.FileCache;
import org.otacoo.chan.core.cache.FileCacheDownloader;
import org.otacoo.chan.core.cache.FileCacheListener;
import org.otacoo.chan.core.cache.FileCacheProvider;
import org.otacoo.chan.core.di.UserAgentProvider;
import org.otacoo.chan.core.model.PostImage;
import org.otacoo.chan.core.settings.ChanSettings;
import org.otacoo.chan.ui.activity.StartActivity;
import org.otacoo.chan.utils.AndroidUtils;
import org.otacoo.chan.utils.Logger;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class MultiImageView extends FrameLayout implements View.OnClickListener, DefaultLifecycleObserver {
    public enum Mode {
        UNLOADED, LOWRES, BIGIMAGE, GIF, MOVIE, OTHER
    }

    private static final String TAG = "MultiImageView";
    //for checkstyle to not be dumb about local final vars
    private static final int BACKGROUND_COLOR = Color.argb(255, 211, 217, 241);
    private static final float[] VLC_CYCLE_SPEED_VALUES = {0.5f, 1.0f, 1.5f, 2.0f};
    private static final float[] VLC_MENU_SPEED_VALUES = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    @Inject
    FileCache fileCache;

    @Inject
    UserAgentProvider userAgent;

    private ImageView playView;

    private PostImage postImage;
    private Callback callback;
    private Mode mode = Mode.UNLOADED;

    private boolean hasContent = false;
    private Call thumbnailCall;
    private FileCacheDownloader bigImageRequest;
    private FileCacheDownloader gifRequest;
    private FileCacheDownloader videoRequest;

    private VideoView videoView;
    private VLCVideoLayout vlcVideoLayout;
    private boolean videoError = false;
    private LibVLC libVLC;
    private MediaPlayer vlcMediaPlayer;

    private View vlcControllerContainer;
    private View vlcController;
    private ImageButton vlcPlayPause;
    private SeekBar vlcSeekBar;
    private TextView vlcPosition;
    private TextView vlcDuration;
    private TextView vlcPlaybackSpeed;
    private ImageButton vlcMute;
    private ImageButton vlcBack;
    private ImageButton vlcDownload;
    private View vlcTopController;

    private boolean isMuted = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateTimeTask = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 1000);
        }
    };

    private boolean backgroundToggle;

    public MultiImageView(Context context) {
        this(context, null);
    }

    public MultiImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MultiImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inject(this);

        setOnClickListener(this);

        playView = new ImageView(getContext());
        playView.setVisibility(View.GONE);
        playView.setImageResource(R.drawable.ic_play_circle_outline_white_48dp);
        addView(playView, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    public void bindPostImage(PostImage postImage, Callback callback) {
        this.postImage = postImage;
        this.callback = callback;

        playView.setVisibility(postImage.type == PostImage.Type.MOVIE ? View.VISIBLE : View.GONE);
    }

    public PostImage getPostImage() {
        return postImage;
    }

    public void setMode(final Mode newMode, boolean center) {
        if (this.mode != newMode) {
//            Logger.test("Changing mode from " + this.mode + " to " + newMode + " for " + postImage.thumbnailUrl);
            this.mode = newMode;

            AndroidUtils.waitForMeasure(this, new AndroidUtils.OnMeasuredCallback() {
                @Override
                public boolean onMeasured(View view) {
                    switch (newMode) {
                        case LOWRES:
                            setThumbnail(postImage.getThumbnailUrl().toString(), center);
                            break;
                        case BIGIMAGE:
                            setBigImage(postImage.imageUrl.toString());
                            break;
                        case GIF:
                            setGif(postImage.imageUrl.toString());
                            break;
                        case MOVIE:
                            setVideo(postImage.imageUrl.toString());
                            break;
                        case OTHER:
                            setOther(postImage.imageUrl.toString());
                            break;
                    }
                    return true;
                }
            });
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public CustomScaleImageView findScaleImageView() {
        CustomScaleImageView bigImage = null;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof CustomScaleImageView) {
                bigImage = (CustomScaleImageView) getChildAt(i);
            }
        }
        return bigImage;
    }

    public boolean isZoomed() {
        CustomScaleImageView scaleImageView = findScaleImageView();
        if (scaleImageView != null) {
            return scaleImageView.getScale() > scaleImageView.getMinScale() + 0.01f;
        }
        return false;
    }

    public GifImageView findGifImageView() {
        GifImageView gif = null;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof GifImageView) {
                gif = (GifImageView) getChildAt(i);
            }
        }
        return gif;
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        if (vlcMediaPlayer != null) {
            vlcMediaPlayer.pause();
            vlcPlayPause.setImageResource(R.drawable.ic_play_circle_filled_white);
        } else if (videoView != null) {
            videoView.pause();
        }
    }

    public void setVolume(boolean muted) {
        this.isMuted = muted;
        if (vlcMediaPlayer != null) {
            vlcMediaPlayer.setVolume(muted ? 0 : 100);
        }
        updateMuteButtonIcon();
    }

    @Override
    public void onClick(View v) {
        if (vlcControllerContainer != null && vlcControllerContainer.getVisibility() == View.VISIBLE) {
            vlcControllerContainer.setVisibility(View.GONE);
        } else if (vlcControllerContainer != null) {
            vlcControllerContainer.setVisibility(View.VISIBLE);
            handler.removeCallbacks(hideControllerTask);
            handler.postDelayed(hideControllerTask, ChanSettings.videoPlayerTimeout.get() * 1000L);
        }

        callback.onTap(this);
    }

    private final Runnable hideControllerTask = () -> {
        if (vlcControllerContainer != null) {
            vlcControllerContainer.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ((StartActivity) getContext()).getLifecycle().addObserver(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ((StartActivity) getContext()).getLifecycle().removeObserver(this);
        cleanup();
    }

    private void setThumbnail(String thumbnailUrl, boolean center) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        if (thumbnailCall != null) {
            return;
        }

        OkHttpClient client = injector().instance(OkHttpClient.class);
        Request request = new Request.Builder().url(thumbnailUrl).build();
        thumbnailCall = client.newCall(request);
        thumbnailCall.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                thumbnailCall = null;
                if (center) {
                    AndroidUtils.runOnUiThread(() -> onError(e));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        try (ResponseBody body = response.body()) {
                            if (body != null) {
                                byte[] data = body.bytes();
                                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                                if (bitmap != null && (!hasContent || mode == Mode.LOWRES)) {
                                    AndroidUtils.runOnUiThread(() -> {
                                        ImageView thumbnail = new ImageView(getContext());
                                        thumbnail.setImageBitmap(bitmap);
                                        onModeLoaded(Mode.LOWRES, thumbnail);
                                    });
                                }
                            }
                        }
                    }
                } finally {
                    response.close();
                    thumbnailCall = null;
                }
            }
        });
    }

    private void setBigImage(String imageUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading big image");
            return;
        }

        if (bigImageRequest != null) {
            return;
        }

        callback.showProgress(this, true);
        bigImageRequest = fileCache.downloadFile(imageUrl, new FileCacheListener() {
            @Override
            public void onProgress(long downloaded, long total) {
                callback.onProgress(MultiImageView.this, downloaded, total);
            }

            @Override
            public void onSuccess(File file) {
                setBigImageFile(file);
            }

            @Override
            public void onFail(boolean notFound) {
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError(new Exception());
                }
            }

            @Override
            public void onCancel() {
            }

            @Override
            public void onEnd() {
                bigImageRequest = null;
                callback.showProgress(MultiImageView.this, false);
            }
        });
    }

    private void setBigImageFile(File file) {
        setBitImageFileInternal(file, true, Mode.BIGIMAGE);
    }

    private void setGif(String gifUrl) {
        if (getWidth() == 0 || getHeight() == 0) {
            Logger.e(TAG, "getWidth() or getHeight() returned 0, not loading");
            return;
        }

        if (gifRequest != null) {
            return;
        }

        callback.showProgress(this, true);
        gifRequest = fileCache.downloadFile(gifUrl, new FileCacheListener() {
            @Override
            public void onProgress(long downloaded, long total) {
                callback.onProgress(MultiImageView.this, downloaded, total);
            }

            @Override
            public void onSuccess(File file) {
                if (!hasContent || mode == Mode.GIF) {
                    setGifFile(file);
                }
            }

            @Override
            public void onFail(boolean notFound) {
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError(new Exception());
                }
            }

            @Override
            public void onCancel() {
            }

            @Override
            public void onEnd() {
                gifRequest = null;
                callback.showProgress(MultiImageView.this, false);
            }
        });
    }

    private void setGifFile(File file) {
        // Decode on a background thread, then post view creation back to main.
        new Thread(() -> {
            GifDrawable drawable;
            try {
                drawable = new GifDrawable(file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
                AndroidUtils.runOnUiThread(() -> onError(new Exception(e.getMessage())));
                return;
            } catch (OutOfMemoryError e) {
                Runtime.getRuntime().gc();
                e.printStackTrace();
                AndroidUtils.runOnUiThread(this::onOutOfMemoryError);
                return;
            }

            // For single-frame GIFs use the scaling image viewer instead.
            if (drawable.getNumberOfFrames() == 1) {
                drawable.recycle();
                AndroidUtils.runOnUiThread(() -> setBitImageFileInternal(file, false, Mode.GIF));
                return;
            }

            // All view operations must happen on the main thread.
            final GifDrawable finalDrawable = drawable;
            AndroidUtils.runOnUiThread(() -> {
                GifImageView view = new GifImageView(getContext());
                view.setImageDrawable(finalDrawable);
                onModeLoaded(Mode.GIF, view);
            });
        }, "gif-decode").start();
    }

    private void setVideo(String videoUrl) {
        if (videoRequest != null) {
            return;
        }

        callback.showProgress(this, true);
        videoRequest = fileCache.downloadFile(videoUrl, new FileCacheListener() {
            @Override
            public void onProgress(long downloaded, long total) {
                callback.onProgress(MultiImageView.this, downloaded, total);
            }

            @Override
            public void onSuccess(File file) {
                if (!hasContent || mode == Mode.MOVIE) {
                    setVideoFile(file);
                }
            }

            @Override
            public void onFail(boolean notFound) {
                if (notFound) {
                    onNotFoundError();
                } else {
                    onError(new Exception());
                }
            }

            @Override
            public void onCancel() {
            }

            @Override
            public void onEnd() {
                videoRequest = null;
                callback.showProgress(MultiImageView.this, false);
            }
        });
    }

    private void setOther(String fileUrl) {
        Toast.makeText(getContext(), R.string.file_not_viewable, Toast.LENGTH_LONG).show();
    }

    private void setVideoFile(final File file) {
        if (ChanSettings.videoOpenExternal.get()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri fileUri = FileCacheProvider.getUriForFile(file);
            intent.setDataAndType(fileUri, "video/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri(null, fileUri));
            AndroidUtils.openIntent(intent);
            onModeLoaded(Mode.MOVIE, videoView);
            return;
        }

        ArrayList<String> options = new ArrayList<>();
        options.add("--no-drop-late-frames");
        options.add("--no-skip-frames");
        options.add("-vv");

        libVLC = new LibVLC(getContext(), options);
        vlcMediaPlayer = new MediaPlayer(libVLC);

        View root = LayoutInflater.from(getContext()).inflate(R.layout.clover_player_view, this, false);
        vlcVideoLayout = root.findViewById(R.id.vlc_video_layout);
        vlcControllerContainer = root.findViewById(R.id.vlc_controller_container);
        vlcController = root.findViewById(R.id.vlc_controller);

        setupVlcController();

        vlcMediaPlayer.attachViews(vlcVideoLayout, null, false, false);

        Media media = new Media(libVLC, file.getAbsolutePath());
        media.setHWDecoderEnabled(true, false);
        media.addOption(":network-caching=1500");

        vlcMediaPlayer.setMedia(media);
        media.release();

        vlcMediaPlayer.setEventListener(event -> {
            switch (event.type) {
                case MediaPlayer.Event.Vout:
                    AndroidUtils.runOnUiThread(() -> {
                        onModeLoaded(Mode.MOVIE, root);
                        checkAudioTracks();
                    });
                    break;
                case MediaPlayer.Event.EncounteredError:
                    onVideoError();
                    break;
                case MediaPlayer.Event.EndReached:
                    AndroidUtils.runOnUiThread(() -> {
                        if (ChanSettings.videoAutoLoop.get()) {
                            // EndReached is terminal. Restarting requires stop() then play().
                            handler.post(() -> {
                                if (vlcMediaPlayer != null) {
                                    vlcMediaPlayer.stop();
                                    vlcMediaPlayer.play();
                                }
                            });
                        } else {
                            vlcPlayPause.setImageResource(R.drawable.ic_play_circle_filled_white);
                        }
                    });
                    break;
                case MediaPlayer.Event.Playing:
                    AndroidUtils.runOnUiThread(() -> {
                        vlcPlayPause.setImageResource(R.drawable.ic_pause_circle_filled_white);
                        handler.post(updateTimeTask);
                    });
                    break;
                case MediaPlayer.Event.Paused:
                    AndroidUtils.runOnUiThread(() -> {
                        vlcPlayPause.setImageResource(R.drawable.ic_play_circle_filled_white);
                        handler.removeCallbacks(updateTimeTask);
                    });
                    break;
            }
        });

        isMuted = ChanSettings.videoDefaultMuted.get();
        vlcMediaPlayer.setVolume(isMuted ? 0 : 100);
        vlcMediaPlayer.play();

        playView.setVisibility(View.GONE);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(root, 0, lp);

        callback.onVideoLoaded(this);
    }

    private void setupVlcController() {
        vlcPlayPause = vlcController.findViewById(R.id.vlc_play_pause);
        vlcSeekBar = vlcController.findViewById(R.id.vlc_progress);
        vlcPosition = vlcController.findViewById(R.id.vlc_position);
        vlcDuration = vlcController.findViewById(R.id.vlc_duration);
        vlcPlaybackSpeed = vlcController.findViewById(R.id.vlc_playback_speed);
        vlcMute = vlcControllerContainer.findViewById(R.id.vlc_mute);
        vlcBack = vlcControllerContainer.findViewById(R.id.vlc_back);
        vlcDownload = vlcControllerContainer.findViewById(R.id.vlc_download);
        vlcTopController = vlcControllerContainer.findViewById(R.id.vlc_top_controller);

        boolean immersive = ChanSettings.useImmersiveModeForGallery.get();
        vlcBack.setVisibility(immersive ? View.VISIBLE : View.GONE);
        vlcDownload.setVisibility(immersive ? View.VISIBLE : View.GONE);
        updateTopControllerVisibility();

        vlcPlayPause.setOnClickListener(v -> {
            if (vlcMediaPlayer.isPlaying()) {
                vlcMediaPlayer.pause();
            } else {
                // 6 is the constant for ENDED state in LibVLC 3.x
                if (vlcMediaPlayer.getPlayerState() == 6) {
                    vlcMediaPlayer.stop();
                }
                vlcMediaPlayer.play();
            }
        });

        vlcSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    vlcMediaPlayer.setTime(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(updateTimeTask);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                handler.post(updateTimeTask);
            }
        });

        vlcController.findViewById(R.id.vlc_rew).setOnClickListener(v -> vlcMediaPlayer.setTime(Math.max(0, vlcMediaPlayer.getTime() - 5000)));
        vlcController.findViewById(R.id.vlc_ffwd).setOnClickListener(v -> vlcMediaPlayer.setTime(Math.min(vlcMediaPlayer.getLength(), vlcMediaPlayer.getTime() + 15000)));

        vlcPlaybackSpeed.setOnClickListener(v -> {
            float currentRate = vlcMediaPlayer.getRate();
            int index = -1;
            for (int i = 0; i < VLC_CYCLE_SPEED_VALUES.length; i++) {
                if (Math.abs(VLC_CYCLE_SPEED_VALUES[i] - currentRate) < 0.01f) {
                    index = i;
                    break;
                }
            }
            index = (index + 1) % VLC_CYCLE_SPEED_VALUES.length;
            float newRate = VLC_CYCLE_SPEED_VALUES[index];
            vlcMediaPlayer.setRate(newRate);
            vlcPlaybackSpeed.setText(String.format(Locale.US, "%.1fx", newRate));
        });

        vlcPlaybackSpeed.setOnLongClickListener(v -> {
            List<FloatingMenuItem> speeds = new ArrayList<>();
            for (int i = 0; i < VLC_MENU_SPEED_VALUES.length; i++) {
                speeds.add(new FloatingMenuItem(i, String.format(Locale.US, "%.2fx", VLC_MENU_SPEED_VALUES[i])));
            }

            FloatingMenu menu = new FloatingMenu(getContext(), vlcPlaybackSpeed, speeds);
            menu.setAnchor(vlcPlaybackSpeed, Gravity.TOP, 0, -dp(10));
            menu.setBackgroundColor(Color.argb(160, 0, 0, 0));
            menu.setForegroundColor(Color.WHITE);
            menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
                @Override
                public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                    if (item != null) {
                        int index = (int) item.getId();
                        float rate = VLC_MENU_SPEED_VALUES[index];
                        vlcMediaPlayer.setRate(rate);
                        vlcPlaybackSpeed.setText(String.format(Locale.US, "%.1fx", rate));
                    }
                }

                @Override
                public void onFloatingMenuDismissed(FloatingMenu menu) {
                }
            });
            menu.show();
            return true;
        });

        vlcMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            setVolume(isMuted);
            callback.onVideoMuteClicked(this, isMuted);
        });

        vlcBack.setOnClickListener(v -> callback.onVideoBackClicked(this));
        vlcDownload.setOnClickListener(v -> callback.onVideoDownloadClicked(this));

        updateMuteButtonIcon();
    }

    private void updateTopControllerVisibility() {
        if (vlcTopController != null) {
            boolean backVisible = vlcBack != null && vlcBack.getVisibility() == View.VISIBLE;
            boolean downloadVisible = vlcDownload != null && vlcDownload.getVisibility() == View.VISIBLE;
            boolean muteVisible = vlcMute != null && vlcMute.getVisibility() == View.VISIBLE;
            vlcTopController.setVisibility(backVisible || downloadVisible || muteVisible ? View.VISIBLE : View.GONE);
        }
    }

    private void updateMuteButtonIcon() {
        if (vlcMute != null) {
            vlcMute.setImageResource(isMuted ? R.drawable.ic_volume_off_white_24dp : R.drawable.ic_volume_up_white_24dp);
        }
    }

    private void updateProgress() {
        if (vlcMediaPlayer == null || vlcSeekBar == null) return;
        long time = vlcMediaPlayer.getTime();
        long length = vlcMediaPlayer.getLength();
        vlcSeekBar.setMax((int) length);
        vlcSeekBar.setProgress((int) time);
        vlcPosition.setText(formatTime(time));
        vlcDuration.setText(formatTime(length));
    }

    private String formatTime(long millis) {
        int seconds = (int) (millis / 1000) % 60;
        int minutes = (int) (millis / (1000 * 60)) % 60;
        int hours = (int) (millis / (1000 * 60 * 60)) % 24;
        if (hours > 0) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void checkAudioTracks() {
        if (vlcMediaPlayer != null) {
            MediaPlayer.TrackDescription[] tracks = vlcMediaPlayer.getAudioTracks();
            if (tracks != null && tracks.length > 1) { // 0 is Disable, so > 1 means at least one audio track
                if (ChanSettings.useImmersiveModeForGallery.get()) {
                    vlcMute.setVisibility(View.VISIBLE);
                }
                callback.onAudioLoaded(this);
            }
        }
        updateTopControllerVisibility();
    }

    private void onVideoError() {
        if (!videoError) {
            videoError = true;
            cleanupVlc();
            callback.onVideoError(this);
        }
    }

    private void cleanupVideo(VideoView videoView) {
        videoView.stopPlayback();
    }

    private void cleanupVlc() {
        handler.removeCallbacks(updateTimeTask);
        handler.removeCallbacks(hideControllerTask);
        if (vlcMediaPlayer != null) {
            vlcMediaPlayer.stop();
            vlcMediaPlayer.detachViews();
            vlcMediaPlayer.release();
            vlcMediaPlayer = null;
        }
        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
    }

    public void toggleTransparency() {
        CustomScaleImageView imageView = findScaleImageView();
        GifImageView gifView = findGifImageView();
        if (imageView == null && gifView == null) return;
        boolean isImage = imageView != null && gifView == null;
        int backgroundColor = backgroundToggle ? Color.TRANSPARENT : BACKGROUND_COLOR;
        if (isImage) {
            imageView.setTileBackgroundColor(backgroundColor);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                gifView.getDrawable().setColorFilter(new BlendModeColorFilter(backgroundColor, BlendMode.DST_OVER));
            } else {
                //noinspection deprecation
                gifView.getDrawable().setColorFilter(backgroundColor, PorterDuff.Mode.DST_OVER);
            }
        }
        backgroundToggle = !backgroundToggle;
    }

    public void setOrientation(int orientation) {
        CustomScaleImageView imageView = findScaleImageView();
        GifImageView gifView = findGifImageView();
        if (imageView == null && gifView == null) return;
        boolean isImage = imageView != null && gifView == null;
        if (isImage) {
            if (orientation < 0) {
                if (orientation == -1) {
                    imageView.setScaleX(-1f);
                    imageView.setScaleY(1f);
                } else {
                    imageView.setScaleX(1f);
                    imageView.setScaleY(-1f);
                }
            } else {
                imageView.setScaleX(1f);
                imageView.setScaleY(1f);
                imageView.setOrientation(orientation);

                float scale;
                if (orientation == 0 || orientation == 180)
                    scale = Math.min(getWidth() / (float) imageView.getSWidth(), getHeight() / (float) imageView.getSHeight());
                else
                    scale = Math.min(getWidth() / (float) imageView.getSHeight(), getHeight() / (float) imageView.getSWidth());
                imageView.setMinScale(scale);

                if (imageView.getMaxScale() < scale * 2f) {
                    imageView.setMaxScale(scale * 2f);
                }
            }
        } else if (gifView != null) {
            if (orientation < 0) {
                if (orientation == -1) {
                    gifView.setScaleX(-1f);
                    gifView.setScaleY(1f);
                } else {
                    gifView.setScaleX(1f);
                    gifView.setScaleY(-1f);
                }
            } else {
                gifView.setScaleX(1f);
                gifView.setScaleY(1f);
                if (orientation == 0) {
                    gifView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                } else {
                    gifView.setScaleType(ImageView.ScaleType.MATRIX);
                    int iw = gifView.getDrawable().getIntrinsicWidth();
                    int ih = gifView.getDrawable().getIntrinsicHeight();
                    RectF dstRect = new RectF(0, 0, gifView.getWidth(), gifView.getHeight());
                    Matrix matrix = new Matrix();
                    if (orientation == 90 || orientation == 270) {
                        matrix.setRectToRect(new RectF(0, 0, ih, iw), dstRect, Matrix.ScaleToFit.CENTER);
                        matrix.preRotate(90f, ih / 2, ih / 2);
                    } else {
                        matrix.setRectToRect(new RectF(0, 0, iw, ih), dstRect, Matrix.ScaleToFit.CENTER);
                    }
                    if (orientation >= 180)
                        matrix.preRotate(180f, iw / 2, ih / 2);
                    gifView.setImageMatrix(matrix);
                }
            }
        }
    }

    private void setBitImageFileInternal(File file, boolean tiling, final Mode forMode) {
        final CustomScaleImageView image = new CustomScaleImageView(getContext());
        image.setImage(com.davemorrissey.labs.subscaleview.ImageSource.uri(file.getAbsolutePath()).tiling(tiling));
        image.setOnClickListener(MultiImageView.this);
        addView(image, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        image.setCallback(new CustomScaleImageView.Callback() {
            @Override
            public void onReady() {
                if (!hasContent || mode == forMode) {
                    callback.showProgress(MultiImageView.this, false);
                    onModeLoaded(Mode.BIGIMAGE, image);
                }
            }

            @Override
            public void onError(boolean wasInitial) {
                onBigImageError(wasInitial);
            }
        });
    }

    private void onError(Exception e) {
        String message = getContext().getString(R.string.image_preview_failed);
        String extra = e.getMessage() == null ? "" : ": " + e.getMessage();
        Toast.makeText(getContext(), message + extra, Toast.LENGTH_SHORT).show();
        callback.showProgress(this, false);
    }

    private void onNotFoundError() {
        callback.showProgress(this, false);
        Toast.makeText(getContext(), R.string.image_not_found, Toast.LENGTH_SHORT).show();
    }

    private void onOutOfMemoryError() {
        Toast.makeText(getContext(), R.string.image_preview_failed_oom, Toast.LENGTH_SHORT).show();
        callback.showProgress(this, false);
    }

    private void onBigImageError(boolean wasInitial) {
        if (wasInitial) {
            Toast.makeText(getContext(), R.string.image_failed_big_image, Toast.LENGTH_SHORT).show();
            callback.showProgress(this, false);
        }
    }

    public void cleanup() {
        if (thumbnailCall != null) {
            thumbnailCall.cancel();
            thumbnailCall = null;
        }
        if (bigImageRequest != null) {
            bigImageRequest.cancel();
            bigImageRequest = null;
        }
        if (gifRequest != null) {
            gifRequest.cancel();
            gifRequest = null;
        }
        if (videoRequest != null) {
            videoRequest.cancel();
            videoRequest = null;
        }
        
        // Stop all active view content
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof VideoView) {
                cleanupVideo((VideoView) child);
            } else if (child instanceof GifImageView) {
                GifImageView gif = (GifImageView) child;
                if (gif.getDrawable() instanceof GifDrawable) {
                    ((GifDrawable) gif.getDrawable()).stop();
                }
            }
        }

        cleanupVlc();
    }

    private void onModeLoaded(Mode mode, View view) {
        if (view != null) {
            // Remove all other views
            boolean alreadyAttached = false;
            for (int i = getChildCount() - 1; i >= 0; i--) {
                View child = getChildAt(i);
                if (child != playView) {
                    if (child != view) {
                        if (child instanceof VideoView) {
                            cleanupVideo((VideoView) child);
                        }

                        removeViewAt(i);
                    } else {
                        alreadyAttached = true;
                    }
                }
            }

            if (!alreadyAttached) {
                addView(view, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
        }

        hasContent = true;
        callback.onModeLoaded(this, mode);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child instanceof GifImageView) {
            GifImageView gif = (GifImageView) child;
            if (gif.getDrawable() instanceof GifDrawable) {
                GifDrawable drawable = (GifDrawable) gif.getDrawable();
                if (drawable.getFrameByteCount() > 100 * 1024 * 1024) { //max size from RecordingCanvas
                    onError(new Exception("Uncompressed GIF too large (>100MB)"));
                    return false;
                }
            }
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public interface Callback {
        void onTap(MultiImageView multiImageView);

        void showProgress(MultiImageView multiImageView, boolean progress);

        void onProgress(MultiImageView multiImageView, long current, long total);

        void onVideoError(MultiImageView multiImageView);

        void onVideoLoaded(MultiImageView multiImageView);

        void onModeLoaded(MultiImageView multiImageView, Mode mode);

        void onAudioLoaded(MultiImageView multiImageView);

        void onVideoMuteClicked(MultiImageView multiImageView, boolean muted);

        void onVideoBackClicked(MultiImageView multiImageView);

        void onVideoDownloadClicked(MultiImageView multiImageView);
    }

    public static class NoMusicServiceCommandContext extends ContextWrapper {
        public NoMusicServiceCommandContext(Context base) {
            super(base);
        }

        @Override
        public void sendBroadcast(Intent intent) {
            // Only allow broadcasts when it's not a music service command
            // Prevents pause intents from broadcasting
            if (!"com.android.music.musicservicecommand".equals(intent.getAction())) {
                super.sendBroadcast(intent);
            }
        }
    }
}
