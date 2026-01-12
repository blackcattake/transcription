package com.example.master

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.master.R
import org.vosk.android.StorageService
import org.vosk.Model
import org.vosk.android.SpeechService

class MainActivity : AppCompatActivity(), org.vosk.android.RecognitionListener {

    private val RECORD_REQUEST_CODE = 101
    private var model: Model? = null
    private var speechService: SpeechService? = null

    private lateinit var resultTextView: TextView
    private lateinit var toggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultTextView = findViewById(R.id.result_text)
        toggleButton = findViewById(R.id.toggle_button)
        toggleButton.setOnClickListener { toggleRecognition() }
        toggleButton.isEnabled = false // モデルロード完了まで無効化

        // 権限チェックと初期化を開始
        checkPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService?.stop()
        speechService?.shutdown()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_REQUEST_CODE
            )
        } else {
            // 権限がある場合はモデルの初期化に進む
            initializeVosk()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeVosk()
        } else {
            resultTextView.text = "録音権限が必要です"
        }
    }

    private fun initializeVosk() {
        resultTextView.text = "モデルをロード中..."
        Log.d("VoskSetup", "Unpack開始")

        // モデルのロードはバックグラウンドスレッドで実行
        StorageService.unpack(this, "model", "model",
            { model ->
                // 成功時の処理
                this.model = model
                resultTextView.text = "モデルロード完了。録音を開始できます。"
                toggleButton.isEnabled = true
            },
            { exception ->
                // 失敗時の処理
                resultTextView.text = "モデルロード失敗: ${exception.message}"
                Log.e("VoskSetup", "モデルのロードに失敗しました", exception)
            })
    }

    private fun toggleRecognition() {
        if (speechService != null) {
            // 認識サービスを停止
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            toggleButton.text = "録音開始"
            resultTextView.text = "認識停止"
        } else {
            // 認識サービスを開始
            try {
                // 1. Nullチェック
                if (model == null) throw IllegalStateException("Model not loaded")

                val recognizer = org.vosk.Recognizer(model!!,16000f)

                // 2. SpeechServiceのセットアップと開始

                speechService = SpeechService(recognizer, 16000f)
                speechService?.startListening(this)

                toggleButton.text = "録音停止"
                resultTextView.text = "音声入力待ち..."
            } catch (e: Exception) {
                // 3. 保険：Logcatに出力
                android.util.Log.e("VoskApp", "認識開始エラーが発生しました", e)
                resultTextView.text = "認識開始エラー: ${e.message}"
            }
        }
    }

    // RecognitionListener インターフェースの実装

    override fun onResult(hypothesis: String) {
        // 最終的な認識結果 (JSON形式)
        // 例: {"text" : "こんにちは"}
        resultTextView.text = "結果: $hypothesis"
    }

    override fun onFinalResult(hypothesis: String) {
        // 認識完了時の最終結果 (ストリーミング認識を停止せずに継続する場合に使う)
        onResult(hypothesis)
    }

    override fun onPartialResult(partialResult: String) {
        // 部分的な認識結果 (リアルタイムで表示を更新する場合に使う)
        // 例: {"partial" : "こんに"}
        resultTextView.text = "認識中: $partialResult"
    }

    override fun onError(exception: Exception) {
        resultTextView.text = "認識エラー: ${exception.message}"
        toggleButton.text = "録音開始"
        speechService = null
    }

    override fun onTimeout() {
        resultTextView.text = "タイムアウト"
        toggleButton.text = "録音開始"
        speechService = null
    }
}