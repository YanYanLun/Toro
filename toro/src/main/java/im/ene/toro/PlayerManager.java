/*
 * Copyright 2016 eneim@Eneim Labs, nam@ene.im
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

package im.ene.toro;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import java.util.ArrayList;

/**
 * Created by eneim on 1/29/16.
 */
public interface PlayerManager extends Removable {

  class Factory {
    @SuppressWarnings("WeakerAccess") public static PlayerManager getInstance() {
      return new PlayerManagerImpl();
    }
  }

  /* BEGIN Setup current Player */

  /**
   * @return latest Video Player
   */
  @Nullable ToroPlayer getPlayer();

  /**
   * Set current video player. There would be at most one Video player at a time.
   * Implement constraint: an idling or completed player must not belong to any player manager.
   *
   * @param player the current Video Player of this manager
   */
  void setPlayer(ToroPlayer player);

  /* END Setup current unique Player */

  /* BEGIN Setup own life cycle */

  /**
   * Called after being registered to a RecyclerView. See {@link Toro#register(RecyclerView)}
   */
  void onRegistered();

  /**
   * Called before being unregistered from a RecyclerView. See {@link
   * Toro#unregister(RecyclerView)}
   */
  void onUnregistered();

  /* END Setup own life cycle */

  /* BEGIN Directly control current player */

  /**
   * Start playing current video
   */
  void startPlayback();

  /**
   * Pause current video
   */
  void pausePlayback();

  /**
   * Stop current video. Used when the Video is detached from its parent.
   */
  void stopPlayback();

  /**
   * Save current video playback state.
   *
   * @param videoId the unique Id of video inside the RecyclerView.
   * @param position current playing position, can be {@code null}.
   * @param duration duration of playing video.
   */
  @Deprecated void saveVideoState(String videoId, @Nullable Long position, long duration);

  /**
   * Save current video playback state.
   *
   * @param mediaId the unique Id of video inside the RecyclerView.
   * @param position current playing position, can be {@code null}.
   * @param duration duration of playing video.
   */
  void savePlaybackState(String mediaId, @Nullable Long position, long duration);

  /**
   * Restore and setup state of a Video to current video player
   *
   * @param videoId the unique Id of video inside the RecyclerView.
   */
  @Deprecated void restoreVideoState(String videoId);

  /**
   * Restore and setup state of a Video to current video player
   *
   * @param mediaId the unique Id of video inside the RecyclerView.
   */
  void restorePlaybackState(String mediaId);

  @Deprecated @Nullable PlaybackState getSavedState(String videoId);

  @Nullable PlaybackState getPlaybackState(String mediaId);
  /* END Directly control current player */

  @NonNull ArrayList<PlaybackState> getPlaybackStates();
}
