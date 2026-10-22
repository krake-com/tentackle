/*
 * Tentackle - https://tentackle.org.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package org.tentackle.fx;

import org.tentackle.common.Service;
import org.tentackle.log.Logger;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.util.Duration;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.net.URL;

/**
 * Default implementation of the input alert.
 * <p>
 * The default sound is the bundled {@value #DEFAULT_SOUND_NAME}, played via {@link Clip}.
 * The visual alert sets the pseudo-class {@code :tt-alert} for {@value #FLASH_MILLIS} ms.
 *
 * @author harald
 */
@Service(InputAlert.class)
public class DefaultInputAlert implements InputAlert {

  private static final Logger LOGGER = Logger.get(DefaultInputAlert.class);

  /** duration of the visual alert in milliseconds. */
  public static final int FLASH_MILLIS = 150;

  /** resource name of the default sound, relative to this class. */
  public static final String DEFAULT_SOUND_NAME = "input-alert.wav";

  /** the default sound: a short, soft bump (120 ms, 16-bit mono). */
  public static final URL DEFAULT_SOUND = DefaultInputAlert.class.getResource(DEFAULT_SOUND_NAME);

  private static final PseudoClass ALERT_PSEUDO_CLASS = PseudoClass.getPseudoClass(PSEUDO_CLASS);
  private static final String TRANSITION_KEY = DefaultInputAlert.class.getName() + ".transition";


  private final BooleanProperty soundEnabled = new SimpleBooleanProperty(false);
  private final BooleanProperty visualEnabled = new SimpleBooleanProperty(true);

  private URL sound = DEFAULT_SOUND;    // the sound to play
  private Clip clip;            // the loaded clip, null if not loaded yet
  private boolean clipFailed;   // true if loading the clip failed


  /**
   * Creates the input alert.
   */
  public DefaultInputAlert() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  @Override
  public void alert(Node node, Reason reason) {
    if (node != null && node.getProperties().containsKey(DISABLED)) {
      return;
    }
    if (isSoundEnabled()) {
      playSound();
    }
    if (node != null && isVisualEnabled()) {
      flash(node);
    }
  }

  @Override
  public BooleanProperty soundEnabledProperty() {
    return soundEnabled;
  }

  @Override
  public BooleanProperty visualEnabledProperty() {
    return visualEnabled;
  }

  @Override
  public synchronized void setSound(URL sound) {
    this.sound = sound == null ? DEFAULT_SOUND : sound;
    closeClip();
  }

  @Override
  public synchronized URL getSound() {
    return sound;
  }


  /**
   * Plays the sound.<br>
   * The clip is loaded and started in a virtual thread in order not to block the FX thread.
   */
  protected void playSound() {
    Thread.ofVirtual().name("input-alert").start(() -> {
      synchronized (this) {
        Clip loadedClip = getClip();
        if (loadedClip != null && !loadedClip.isRunning()) {   // don't stack alerts, e.g. for auto-repeated keys
          loadedClip.setFramePosition(0);
          loadedClip.start();
        }
      }
    });
  }

  /**
   * Shows the visual alert.
   *
   * @param node the node
   */
  protected void flash(Node node) {
    node.pseudoClassStateChanged(ALERT_PSEUDO_CLASS, true);
    PauseTransition transition = (PauseTransition) node.getProperties().computeIfAbsent(TRANSITION_KEY, _ -> {
      PauseTransition pause = new PauseTransition(Duration.millis(FLASH_MILLIS));
      pause.setOnFinished(_ -> node.pseudoClassStateChanged(ALERT_PSEUDO_CLASS, false));
      return pause;
    });
    transition.playFromStart();
  }

  /**
   * Gets the clip, loads it if necessary.
   *
   * @return the clip, null if no sound available
   */
  protected synchronized Clip getClip() {
    if (clip == null && !clipFailed) {
      try {
        clip = loadClip();
      }
      catch (LineUnavailableException | UnsupportedAudioFileException | IOException | RuntimeException ex) {
        clipFailed = true;    // don't try again
        LOGGER.warning("input alert sound is not available", ex);
      }
    }
    return clip;
  }

  /**
   * Loads the clip.
   *
   * @return the opened clip
   * @throws LineUnavailableException if there is no audio line
   * @throws UnsupportedAudioFileException if the sound is not supported
   * @throws IOException if the sound could not be read
   */
  protected Clip loadClip() throws LineUnavailableException, UnsupportedAudioFileException, IOException {
    if (sound == null) {
      throw new IOException("sound resource " + DEFAULT_SOUND_NAME + " not found");
    }
    Clip newClip = AudioSystem.getClip();
    try (AudioInputStream stream = AudioSystem.getAudioInputStream(sound)) {
      newClip.open(stream);
    }
    catch (LineUnavailableException | UnsupportedAudioFileException | IOException | RuntimeException ex) {
      newClip.close();
      throw ex;
    }
    return newClip;
  }

  /**
   * Closes the clip.<br>
   * The next alert will load the clip again.
   */
  protected synchronized void closeClip() {
    if (clip != null) {
      clip.close();
      clip = null;
    }
    clipFailed = false;
  }


}
