package com.vforkorea.assemblehelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class CaptchaDialogActivity : AppCompatActivity() {

    private lateinit var radioGroupChoice: RadioGroup
    private lateinit var radioCons: RadioButton
    private lateinit var radioPros: RadioButton
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button

    private lateinit var prefHelper: PreferenceHelper

    companion object {
        private const val TAG = "CaptchaDialog"
        const val EXTRA_CAPTCHA_URL = "captcha_url"
        const val ACTION_CAPTCHA_RESULT = "com.vforkorea.opinionhelper.CAPTCHA_RESULT"
        const val EXTRA_CHOICE = "choice"
        const val ACTION_CLOSE_DIALOG = "com.vforkorea.opinionhelper.CLOSE_DIALOG"
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "========== onCreate 시작 ==========")

        try {
            setContentView(R.layout.activity_captcha_dialog)
            Log.d(TAG, "✅ setContentView 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ setContentView 실패: ${e.message}")
            e.printStackTrace()
            finish()
            return
        }

        prefHelper = PreferenceHelper(this)

        // UI 초기화
        try {
            radioGroupChoice = findViewById(R.id.radioGroupChoice)
            radioCons = findViewById(R.id.radioCons)
            radioPros = findViewById(R.id.radioPros)
            btnSubmit = findViewById(R.id.btnSubmit)
            btnCancel = findViewById(R.id.btnCancel)

            Log.d(TAG, "✅ UI 요소 초기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ UI 초기화 실패: ${e.message}")
            e.printStackTrace()
            finish()
            return
        }

        // 저장된 마지막 선택값 복원
        val lastChoice = prefHelper.getLastChoice()
        Log.d(TAG, "마지막 선택값: $lastChoice")

        if (lastChoice == PreferenceHelper.CHOICE_PROS) {
            radioPros.isChecked = true
        } else {
            radioCons.isChecked = true
        }

        // 버튼 이벤트
        btnSubmit.setOnClickListener {
            Log.d(TAG, "확인 버튼 클릭")
            submitChoice()
        }
        btnCancel.setOnClickListener {
            Log.d(TAG, "취소 버튼 클릭")
            finish()
        }

        // 닫기 이벤트 등록
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(closeReceiver, IntentFilter(ACTION_CLOSE_DIALOG))

        Log.d(TAG, "========== onCreate 완료 ==========")
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(closeReceiver)
        Log.d(TAG, "onDestroy")
    }

    /**
     * 찬반 선택 제출
     */
    private fun submitChoice() {
        // 선택값 확인
        val choice = if (radioPros.isChecked) {
            PreferenceHelper.CHOICE_PROS
        } else {
            PreferenceHelper.CHOICE_CONS
        }

        Log.d(TAG, "✅ 선택: $choice")

        // 선택값 저장
        prefHelper.saveLastChoice(choice)

        // 결과 전송 (캡차 값 없이)
        val resultIntent = Intent(ACTION_CAPTCHA_RESULT).apply {
            putExtra(EXTRA_CHOICE, choice)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent)

        Log.d(TAG, "📤 결과 브로드캐스트 전송 완료")

        // 다이얼로그 닫기
        finish()
    }
}