/*
 * Copyright 2017 eneim@Eneim Labs, nam@ene.im
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.ene.toro.exoplayer2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.os.Handler;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Util;
import java.util.UUID;

import static im.ene.toro.exoplayer2.ExoPlayerHelper.BANDWIDTH_METER;
import static im.ene.toro.exoplayer2.ExoPlayerHelper.buildDataSourceFactory;
import static im.ene.toro.exoplayer2.ExoPlayerHelper.buildDrmSessionManager;
import static im.ene.toro.exoplayer2.ExoPlayerHelper.buildMediaSource;
import static im.ene.toro.exoplayer2.ExoPlayerHelper.getDrmUuid;

/**
 * Created by eneim on 2/7/17.
 */

public class ExoPlayerView extends FrameLayout implements ExoPlayer.EventListener {

  private final SimpleExoPlayerView playerView;
  private final Handler mainHandler = new Handler();
  private final int extensionMode;

  PlayerCallback playerCallback;

  public ExoPlayerView(@NonNull Context context) {
    this(context, null);
  }

  public ExoPlayerView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ExoPlayerView(@NonNull Context context, @Nullable AttributeSet attrs,
      @AttrRes int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    playerView = new SimpleExoPlayerView(context, attrs, defStyleAttr);
    addView(playerView, 0);

    if (attrs != null) {
      TypedArray a =
          context.getTheme().obtainStyledAttributes(attrs, R.styleable.ExoPlayerView, 0, 0);
      try {
        extensionMode = a.getInt(R.styleable.ExoPlayerView_extension_mode,
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
      } finally {
        a.recycle();
      }
    } else {
      extensionMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
    }
  }

  private MediaSource mediaSource;

  private DefaultTrackSelector trackSelector;
  private boolean needRetrySource;
  private boolean shouldAutoPlay;
  private int resumeWindow;
  private long resumePosition;

  // public methods //

  public void setMedia(Media media, boolean shouldAutoPlay) throws ParserException {
    setMedia(media, shouldAutoPlay, buildDataSourceFactory(getContext(), true));
  }

  public void setMedia(Media media, boolean shouldAutoPlay,
      DataSource.Factory mediaDataSourceFactory) throws ParserException {
    MediaSource mediaSource =
        buildMediaSource(getContext(), media.getMediaUri(), mediaDataSourceFactory, mainHandler,
            null);
    setMediaSource(mediaSource, shouldAutoPlay);
  }

  public void setMediaSource(MediaSource source, boolean shouldAutoPlay) throws ParserException {
    if (source == null || source.equals(this.mediaSource)) { // including null
      return;
    }

    this.mediaSource = source;
    this.shouldAutoPlay = shouldAutoPlay;
    initializePlayer();
  }

  public void setPlayerCallback(PlayerCallback playerCallback) {
    this.playerCallback = playerCallback;
  }

  public void hideController() {
    this.playerView.hideController();
  }

  public void showController() {
    this.playerView.showController();
  }

  public void setControllerShowTimeoutMs(int millisecond) {
    this.playerView.setControllerShowTimeoutMs(millisecond);
  }

  public void setResizeMode(@AspectRatioFrameLayout.ResizeMode int resizeMode) {
    this.playerView.setResizeMode(resizeMode);
  }

  public void setUseArtwork(boolean useArtwork) {
    playerView.setUseArtwork(useArtwork);
  }

  public Bitmap getDefaultArtwork() {
    return playerView.getDefaultArtwork();
  }

  public void setDefaultArtwork(Bitmap defaultArtwork) {
    playerView.setDefaultArtwork(defaultArtwork);
  }

  public void setUseController(boolean useController) {
    playerView.setUseController(useController);
  }

  public boolean getUseController() {
    return playerView.getUseController();
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) public final void initializePlayer()
      throws ParserException {
    if (mediaSource == null) {
      throw new IllegalStateException("Media Source must not be null.");
    }

    SimpleExoPlayer player = playerView.getPlayer();
    boolean needNewPlayer = player == null;
    if (needNewPlayer) {
      UUID drmSchemeUuid =
          mediaSource instanceof DrmMedia ? getDrmUuid(((DrmMedia) mediaSource).getType()) : null;
      DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
      if (drmSchemeUuid != null) {
        String drmLicenseUrl = ((DrmMedia) mediaSource).getLicenseUrl();
        String[] keyRequestPropertiesArray =
            ((DrmMedia) mediaSource).getKeyRequestPropertiesArray();

        try {
          drmSessionManager = buildDrmSessionManager(getContext(), drmSchemeUuid, drmLicenseUrl,
              keyRequestPropertiesArray, mainHandler);
        } catch (UnsupportedDrmException e) {
          int errorStringId = Util.SDK_INT < 18 ? R.string.error_drm_not_supported
              : (e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                  ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
          Toast.makeText(getContext(), errorStringId, Toast.LENGTH_SHORT).show();
          return;
        }
      }

      TrackSelection.Factory videoTrackSelectionFactory =
          new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
      trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

      DefaultRenderersFactory renderersFactory =
          new DefaultRenderersFactory(getContext(), drmSessionManager, extensionMode);

      player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector);
      player.addListener(this);

      playerView.setPlayer(player);
      player.setPlayWhenReady(shouldAutoPlay);
      needRetrySource = true;
    }

    if (needNewPlayer || needRetrySource) {
      boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
      if (haveResumePosition) {
        player.seekTo(resumeWindow, resumePosition);
      }
      player.prepare(mediaSource, !haveResumePosition, false);
      needRetrySource = false;
    }
  }

  public final void releasePlayer() {
    SimpleExoPlayer player = playerView.getPlayer();
    if (player != null) {
      shouldAutoPlay = player.getPlayWhenReady();
      updateResumePosition();
      player.removeListener(this);
      player.release();
      playerView.setPlayer(null);
      trackSelector = null;
    }

    this.mediaSource = null;
  }

  public long getResumePosition() {
    return resumePosition;
  }

  public void setResumePosition(long resumePosition) {
    this.resumePosition = resumePosition;
  }

  @Override protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    releasePlayer();
  }

  // private methods //

  private void updateResumePosition() {
    SimpleExoPlayer player = playerView.getPlayer();
    if (player != null) {
      resumeWindow = player.getCurrentWindowIndex();
      resumePosition = player.isCurrentWindowSeekable() ? //
          Math.max(0, player.getCurrentPosition()) : C.TIME_UNSET;
    }
  }

  private void clearResumePosition() {
    resumeWindow = C.INDEX_UNSET;
    resumePosition = C.TIME_UNSET;
  }

  private static boolean isBehindLiveWindow(ExoPlaybackException e) {
    if (e.type != ExoPlaybackException.TYPE_SOURCE) {
      return false;
    }
    Throwable cause = e.getSourceException();
    while (cause != null) {
      if (cause instanceof BehindLiveWindowException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  // Implement listeners

  @Override public void onTimelineChanged(Timeline timeline, Object manifest) {
    // TODO ??
  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    MappingTrackSelector.MappedTrackInfo mappedTrackInfo =
        trackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo != null) {
      if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_VIDEO)
          == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
        Toast.makeText(getContext(), R.string.error_unsupported_video, Toast.LENGTH_SHORT).show();
      }
      if (mappedTrackInfo.getTrackTypeRendererSupport(C.TRACK_TYPE_AUDIO)
          == MappingTrackSelector.MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
        Toast.makeText(getContext(), R.string.error_unsupported_audio, Toast.LENGTH_SHORT).show();
      }
    }
  }

  @Override public void onLoadingChanged(boolean isLoading) {
    // Do nothing
  }

  @Override public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (this.playerCallback != null) {
      this.playerCallback.onPlayerStateChanged(playWhenReady, playbackState);
    }
  }

  @Override public void onPlayerError(ExoPlaybackException e) {
    String errorString = null;
    if (e.type == ExoPlaybackException.TYPE_RENDERER) {
      Exception cause = e.getRendererException();
      if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
        // Special case for decoder initialization failures.
        MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
            (MediaCodecRenderer.DecoderInitializationException) cause;
        if (decoderInitializationException.decoderName == null) {
          if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
            errorString = getContext().getString(R.string.error_querying_decoders);
          } else if (decoderInitializationException.secureDecoderRequired) {
            errorString = getContext().getString(R.string.error_no_secure_decoder,
                decoderInitializationException.mimeType);
          } else {
            errorString = getContext().getString(R.string.error_no_decoder,
                decoderInitializationException.mimeType);
          }
        } else {
          errorString = getContext().getString(R.string.error_instantiating_decoder,
              decoderInitializationException.decoderName);
        }
      }
    }
    if (errorString != null) {
      Toast.makeText(getContext(), errorString, Toast.LENGTH_SHORT).show();
    }

    needRetrySource = true;
    if (isBehindLiveWindow(e)) {
      clearResumePosition();
      try {
        initializePlayer();
      } catch (ParserException e1) {
        e1.printStackTrace();
      }
    } else {
      updateResumePosition();
    }
  }

  @Override public void onPositionDiscontinuity() {
    if (needRetrySource) {
      // This will only occur if the user has performed a seek whilst in the error state. Update the
      // resume position so that if the user then retries, playback will resume from the position to
      // which they seek.
      updateResumePosition();
    }
  }

  @Override public void onPlaybackParametersChanged(PlaybackParameters parameters) {
    // TODO implement this if need
    Log.d("ToroLib:ExoPlayerView",
        "onPlaybackParametersChanged() called with: parameters = [" + parameters + "]");
  }

  // Implement player interface

  public SimpleExoPlayer getPlayer() {
    return this.playerView.getPlayer();
  }

  public void start() {
    if (getPlayer() != null) {
      getPlayer().setPlayWhenReady(true);
    }
  }

  public void pause() {
    if (getPlayer() != null) {
      getPlayer().setPlayWhenReady(false);
    }
  }

  public long getDuration() {
    return getPlayer() != null ? getPlayer().getDuration() : C.LENGTH_UNSET;
  }

  public long getCurrentPosition() {
    return getPlayer() != null ? getPlayer().getCurrentPosition() : resumePosition;
  }

  public void seekTo(long pos) {
    if (getPlayer() != null) {
      getPlayer().seekTo(pos);
    }
  }

  public boolean isPlaying() {
    return getPlayer() != null && getPlayer().getPlayWhenReady();
  }

  public int getBufferPercentage() {
    return getPlayer() != null ? getPlayer().getBufferedPercentage() : 0;
  }

  public void stop() {
    if (getPlayer() != null) {
      getPlayer().stop();
    }
  }

  public void setVolume(float volume) {
    if (getPlayer() != null) {
      getPlayer().setVolume(volume);
    }
  }

  @Override public String toString() {
    return "ExoPlayerView@" + hashCode();
  }
}
