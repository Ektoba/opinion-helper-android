package com.vforkorea.assemblehelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvTitle: TextView
    private lateinit var cardGuide: MaterialCardView
    private lateinit var cardAccessibility: MaterialCardView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnEnableAccessibility: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 초기화
        webView = findViewById(R.id.webView)
        tvTitle = findViewById(R.id.tvTitle)
        cardGuide = findViewById(R.id.cardGuide)
        cardAccessibility = findViewById(R.id.cardAccessibility)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)

        // 접근성 권한 버튼
        btnEnableAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        // WebView 설정
        setupWebView()

        // 뒤로가기 버튼 처리 (최신 방식)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // vforkorea.com/assem 로드
        webView.loadUrl("https://vforkorea.com/assem")
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    /**
     * WebView 설정
     */
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(false)
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url.toString()

                // 국회 사이트 링크는 기본 브라우저로 열기
                if (url.contains("pal.assembly.go.kr") ||
                    url.contains("assembly.go.kr")) {
                    openInBrowser(url)
                    return true
                }

                return false
            }
        }
    }

    /**
     * 외부 브라우저로 URL 열기
     */
    private fun openInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 접근성 설정 화면 열기
     */
    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 접근성 서비스 활성화 상태 확인 및 UI 업데이트
     */
    private fun updateAccessibilityStatus() {
        val isEnabled = OpinionAutoInputService.isServiceEnabled(this)

        if (isEnabled) {
            // 접근성 활성화됨 - WebView만 표시
            tvTitle.visibility = View.GONE
            cardGuide.visibility = View.GONE
            cardAccessibility.visibility = View.GONE

            tvAccessibilityStatus.text = getString(R.string.accessibility_enabled)
            tvAccessibilityStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
            btnEnableAccessibility.isEnabled = false
            btnEnableAccessibility.alpha = 0.5f

        } else {
            // 접근성 비활성화 - 모든 안내 표시
            tvTitle.visibility = View.VISIBLE
            cardGuide.visibility = View.VISIBLE
            cardAccessibility.visibility = View.VISIBLE

            tvAccessibilityStatus.text = getString(R.string.accessibility_disabled)
            tvAccessibilityStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
            btnEnableAccessibility.isEnabled = true
            btnEnableAccessibility.alpha = 1.0f
        }
    }
}