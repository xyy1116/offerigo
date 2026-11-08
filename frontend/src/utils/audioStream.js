/**
 * 浏览器麦克风音频采集：将麦克风音频重采样为 16kHz 16bit 单声道 PCM，
 * 通过 onPcm 回调输出 Int16Array 供 WebSocket 推送到后端语音识别。
 *
 * 设计说明：
 * 1. 采集时开启回声消除、降噪、自动增益（由浏览器底层算法承担语音增强）；
 * 2. 客户端持续全量推送 PCM（不裁剪静音），把断句交给服务端 Paraformer 的
 *    VAD 完成，从而避免句首/句尾被截断导致识别率下降；
 * 3. 能量检测（VAD）仅用于驱动「打断 + 说话状态/波纹可视化」，不影响音频推送。
 */
export class MicRecorder {
  constructor({
    onPcm,
    onError,
    onSpeechStart,
    onSpeechEnd,
    onTurnEnd,
    onVolume,
    sampleRate = 16000,
    bufferSize = 4096,
    speechThreshold = 0.01,
    silenceHoldFrames = 6,
    onsetHoldFrames = 2,
    endSilenceMs = 800,
    highpassFreq = 200,
    lowpassFreq = 8000
  } = {}) {
    this.onPcm = onPcm
    this.onError = onError
    this.onSpeechStart = onSpeechStart
    this.onSpeechEnd = onSpeechEnd
    this.onTurnEnd = onTurnEnd
    this.onVolume = onVolume
    this.sampleRate = sampleRate
    this.bufferSize = bufferSize
    this.speechThreshold = speechThreshold
    this.silenceHoldFrames = silenceHoldFrames
    this.onsetHoldFrames = onsetHoldFrames
    this.endSilenceMs = endSilenceMs
    this.highpassFreq = highpassFreq
    this.lowpassFreq = lowpassFreq
    this.speaking = false
    this.onsetFrames = 0
    this.silenceFrames = 0
    this.silenceMs = 0
    this.turnEnded = false
    this.hasSpoken = false
    this.stream = null
    this.audioContext = null
    this.source = null
    this.processor = null
    this.highpass = null
    this.lowpass = null
    this.analyser = null
  }

  /**
   * 开始采集。若传入已授权音频流则复用，否则自行请求麦克风权限。
   * @param {MediaStream} existingStream 可选，已包含音频轨道的流
   */
  async start(existingStream) {
    try {
      let audioStream = existingStream
      if (!audioStream || !audioStream.getAudioTracks().length) {
        audioStream = await navigator.mediaDevices.getUserMedia({
          audio: {
            channelCount: 1,
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
          }
        })
      }
      this.stream = audioStream

      const AudioCtx = window.AudioContext || window.webkitAudioContext
      this.audioContext = new AudioCtx({ sampleRate: this.sampleRate })
      this.source = this.audioContext.createMediaStreamSource(this.stream)

      // 降噪滤波链：高通滤除空调/风噪等低频环境声，低通滤除高频嘶声
      this.highpass = this.audioContext.createBiquadFilter()
      this.highpass.type = 'highpass'
      this.highpass.frequency.value = this.highpassFreq
      this.lowpass = this.audioContext.createBiquadFilter()
      this.lowpass.type = 'lowpass'
      this.lowpass.frequency.value = this.lowpassFreq

      // 可视化分析器：高频振幅采样，供前端 30fps+ 波形驱动，不改变音频数据
      this.analyser = this.audioContext.createAnalyser()
      this.analyser.fftSize = 256
      this.analyser.smoothingTimeConstant = 0.5

      this.processor = this.audioContext.createScriptProcessor(this.bufferSize, 1, 1)

      this.processor.onaudioprocess = (e) => {
        const input = e.inputBuffer.getChannelData(0)

        // 全量推送 PCM：断句交给服务端 Paraformer VAD，
        // 句首/句尾/停顿信息完整保留，提升识别准确率
        if (this.onPcm) {
          this.onPcm(this.floatTo16BitPCM(input))
        }

        // 音量与 VAD 仅用于打断机制与波纹可视化，不再裁剪音频
        const rms = this.computeRms(input)
        if (this.onVolume) this.onVolume(rms)

        const frameMs = (this.bufferSize / this.sampleRate) * 1000

        if (rms >= this.speechThreshold) {
          this.silenceMs = 0
          if (!this.speaking) {
            this.onsetFrames++
            // 连续多帧高于阈值才判定为真正发言，过滤键盘声/瞬时噪音，避免状态误切换
            if (this.onsetFrames >= this.onsetHoldFrames) {
              this.speaking = true
              this.hasSpoken = true
              this.turnEnded = false
              if (this.onSpeechStart) this.onSpeechStart()
            }
          }
          this.silenceFrames = 0
        } else {
          this.onsetFrames = 0
          // 静音累计：达到 endSilenceMs 且此前有语音时，判定本次发言结束
          this.silenceMs += frameMs
          if (this.hasSpoken && !this.turnEnded && this.silenceMs >= this.endSilenceMs) {
            this.turnEnded = true
            this.hasSpoken = false
            if (this.onTurnEnd) this.onTurnEnd()
          }
          if (this.speaking) {
            this.silenceFrames++
            if (this.silenceFrames >= this.silenceHoldFrames) {
              this.speaking = false
              this.onsetFrames = 0
              if (this.onSpeechEnd) this.onSpeechEnd()
            }
          }
        }
      }

      this.source.connect(this.highpass)
      this.highpass.connect(this.lowpass)
      this.lowpass.connect(this.analyser)
      this.analyser.connect(this.processor)
      this.processor.connect(this.audioContext.destination)
    } catch (e) {
      if (this.onError) {
        this.onError(e)
      }
      throw e
    }
  }

  computeRms(input) {
    let sum = 0
    for (let i = 0; i < input.length; i++) {
      sum += input[i] * input[i]
    }
    return Math.sqrt(sum / input.length)
  }

  /**
   * 读取当前音频振幅（0~1 归一化 RMS），供前端在 requestAnimationFrame
   * 中以 30fps+ 的频率驱动波形可视化，实现平滑、实时的音量跟随。
   */
  getLevel() {
    const a = this.analyser
    if (!a) return 0
    const data = new Uint8Array(a.fftSize)
    a.getByteTimeDomainData(data)
    let sum = 0
    for (let i = 0; i < data.length; i++) {
      const v = (data[i] - 128) / 128
      sum += v * v
    }
    return Math.sqrt(sum / data.length)
  }

  floatTo16BitPCM(input) {
    const output = new Int16Array(input.length)
    for (let i = 0; i < input.length; i++) {
      const s = Math.max(-1, Math.min(1, input[i]))
      output[i] = s < 0 ? s * 0x8000 : s * 0x7fff
    }
    return output
  }

  /**
   * 恢复被浏览器在后台挂起（suspended）的 AudioContext。
   * 页面切到后台时浏览器可能自动挂起音频上下文，回前台需显式恢复以保证采集与识别持续。
   */
  resume() {
    if (this.audioContext && this.audioContext.state === 'suspended') {
      this.audioContext.resume().catch(() => {})
    }
  }

  stop() {
    try {
      this.processor && this.processor.disconnect()
    } catch (e) {
      // 忽略
    }
    try {
      this.highpass && this.highpass.disconnect()
    } catch (e) {
      // 忽略
    }
    try {
      this.lowpass && this.lowpass.disconnect()
    } catch (e) {
      // 忽略
    }
    try {
      this.analyser && this.analyser.disconnect()
    } catch (e) {
      // 忽略
    }
    try {
      this.source && this.source.disconnect()
    } catch (e) {
      // 忽略
    }
    try {
      this.audioContext && this.audioContext.close()
    } catch (e) {
      // 忽略
    }
    if (this.stream) {
      this.stream.getTracks().forEach((t) => t.stop())
    }
    this.stream = null
    this.audioContext = null
    this.source = null
    this.processor = null
    this.highpass = null
    this.lowpass = null
    this.analyser = null
    this.speaking = false
    this.onsetFrames = 0
    this.silenceFrames = 0
    this.silenceMs = 0
    this.turnEnded = false
    this.hasSpoken = false
  }
}