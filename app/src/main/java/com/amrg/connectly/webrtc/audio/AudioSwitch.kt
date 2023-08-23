package com.amrg.connectly.webrtc.audio

import android.content.Context
import android.media.AudioManager
import timber.log.Timber

class AudioSwitch(
    context: Context,
    audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener,
    private val preferredDeviceList: List<Class<out AudioDevice>>,
    private val audioManager: AudioManagerWrapper = AudioManagerWrapper(
        context,
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
        audioFocusChangeListener
    )
) {

    private var audioDeviceChangeListener: AudioDeviceChangeListener? = null
    private var selectedDevice: AudioDevice? = null
    private var userSelectedDevice: AudioDevice? = null
    private var wiredHeadsetAvailable = false
    private val mutableAudioDevices = ArrayList<AudioDevice>()

    private var state: State = State.STOPPED

    internal enum class State {
        STARTED, ACTIVATED, STOPPED
    }

    fun start(listener: AudioDeviceChangeListener) {
        audioDeviceChangeListener = listener
        when (state) {
            State.STOPPED -> {
                enumerateDevices()
                state = State.STARTED
            }

            else -> {
            }
        }
    }

    fun stop() {
        when (state) {
            State.ACTIVATED -> {
                deactivate()
                closeListeners()
            }

            State.STARTED -> {
                closeListeners()
            }

            State.STOPPED -> {
            }
        }
    }


    fun activate() {
        when (state) {
            State.STARTED -> {
                audioManager.cacheAudioState()

                // Always set mute to false for WebRTC
                audioManager.mute(false)
                audioManager.setAudioFocus()
                selectedDevice?.let { activate(it) }
                state = State.ACTIVATED
            }

            State.ACTIVATED -> selectedDevice?.let { activate(it) }
            State.STOPPED -> throw IllegalStateException()
        }
    }

    private fun deactivate() {
        when (state) {
            State.ACTIVATED -> {
                // Restore stored audio state
                audioManager.restoreAudioState()
                state = State.STARTED
            }

            State.STARTED, State.STOPPED -> {
            }
        }
    }

    private fun selectDevice(audioDevice: AudioDevice?) {
        if (selectedDevice != audioDevice) {
            userSelectedDevice = audioDevice
            enumerateDevices()
        }
    }

    private fun hasNoDuplicates(list: List<Class<out AudioDevice>>) =
        list.groupingBy { it }.eachCount().filter { it.value > 1 }.isEmpty()

    private fun activate(audioDevice: AudioDevice) {
        when (audioDevice) {
            is AudioDevice.BluetoothHeadset -> audioManager.enableSpeakerphone(true)
            is AudioDevice.Earpiece -> audioManager.enableSpeakerphone(true)
            is AudioDevice.WiredHeadset -> audioManager.enableSpeakerphone(false)
            is AudioDevice.Speakerphone -> audioManager.enableSpeakerphone(true)
        }
    }

    internal data class AudioDeviceState(
        val audioDeviceList: List<AudioDevice>,
        val selectedAudioDevice: AudioDevice?
    )

    private fun enumerateDevices(bluetoothHeadsetName: String? = null) {
        // save off the old state and 'semi'-deep copy the list of audio devices
        val oldAudioDeviceState = AudioDeviceState(mutableAudioDevices.map { it }, selectedDevice)
        // update audio device list and selected device
        addAvailableAudioDevices(bluetoothHeadsetName)

        if (!userSelectedDevicePresent(mutableAudioDevices)) {
            userSelectedDevice = null
        }

        // Select the audio device
        selectedDevice = if (userSelectedDevice != null) {
            userSelectedDevice
        } else if (mutableAudioDevices.size > 0) {
            mutableAudioDevices.first()
        } else {
            null
        }

        // Activate the device if in the active state
        if (state == State.ACTIVATED) {
            activate()
        }
        // trigger audio device change listener if there has been a change
        val newAudioDeviceState = AudioDeviceState(mutableAudioDevices, selectedDevice)
        if (newAudioDeviceState != oldAudioDeviceState) {
            audioDeviceChangeListener?.invoke(mutableAudioDevices, selectedDevice)
        }
    }

    private fun addAvailableAudioDevices(bluetoothHeadsetName: String?) {
        Timber.d(
            "[addAvailableAudioDevices] wiredHeadsetAvailable: $wiredHeadsetAvailable, bluetoothHeadsetName: $bluetoothHeadsetName"
        )
        mutableAudioDevices.clear()
        preferredDeviceList.forEach { audioDevice ->
            when (audioDevice) {
                AudioDevice.BluetoothHeadset::class.java -> {
                    /*
                     * Since the there is a delay between receiving the ACTION_ACL_CONNECTED event and receiving
                     * the name of the connected device from querying the BluetoothHeadset proxy class, the
                     * headset name received from the ACTION_ACL_CONNECTED intent needs to be passed into this
                     * function.
                     */
                }

                AudioDevice.WiredHeadset::class.java -> {

                    if (wiredHeadsetAvailable) {
                        mutableAudioDevices.add(AudioDevice.WiredHeadset())
                    }
                }

                AudioDevice.Earpiece::class.java -> {
                    val hasEarpiece = audioManager.hasEarpiece()
                    if (hasEarpiece && !wiredHeadsetAvailable) {
                        mutableAudioDevices.add(AudioDevice.Earpiece())
                    }
                }

                AudioDevice.Speakerphone::class.java -> {
                    val hasSpeakerphone = audioManager.hasSpeakerphone()
                    if (hasSpeakerphone) {
                        mutableAudioDevices.add(AudioDevice.Speakerphone())
                    }
                }
            }
        }
    }

    private fun userSelectedDevicePresent(audioDevices: List<AudioDevice>) =
        userSelectedDevice?.let { selectedDevice ->
            if (selectedDevice is AudioDevice.BluetoothHeadset) {
                // Match any bluetooth headset as a new one may have been connected
                audioDevices.find { it is AudioDevice.BluetoothHeadset }?.let { newHeadset ->
                    userSelectedDevice = newHeadset
                    true
                } ?: false
            } else {
                audioDevices.contains(selectedDevice)
            }
        } ?: false

    private fun closeListeners() {
        audioDeviceChangeListener = null
        state = State.STOPPED
    }

    companion object {
        private val defaultPreferredDeviceList by lazy {
            listOf(
                AudioDevice.BluetoothHeadset::class.java,
                AudioDevice.WiredHeadset::class.java,
                AudioDevice.Earpiece::class.java,
                AudioDevice.Speakerphone::class.java
            )
        }
    }

}