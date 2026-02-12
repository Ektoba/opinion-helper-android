package com.vforkorea.assemblehelper

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class OpinionAutoInputService : AccessibilityService() {

    private lateinit var prefHelper: PreferenceHelper
    private val handler = Handler(Looper.getMainLooper())
    private var isProcessing = false
    private var captchaImageUrl: String? = null
    private var captchaRetryCount = 0  // 👈 추가
    private val MAX_RETRY = 10  // 👈 추가
    // 페이지 안정화 감지용
    private var lastContentChangeTime = 0L  // 👈 추가
    private var pageStabilityCheckRunnable: Runnable? = null  // 👈 추가
    private val STABILITY_DELAY = 800L  // 👈 추가: 페이지가 800ms 동안 변화 없으면 안정화로 판단
    private var processedPageUrl: String? = null  // 👈 추가
    companion object {
        private const val TAG = "OpinionAutoInput"

        // 국회 페이지 패턴
        private val INSERT_PAGE_PATTERNS = arrayOf(
            "lgsltpa/lgsltpaOpn/forInsert.do",
            "napal/lgsltpa/lgsltpaOpn/insert.do"
        )

        private val LIST_PAGE_PATTERNS = arrayOf(
            "lgsltpa/lgsltpaOpn/list.do",
            "napal/lgsltpa/lgsltpaOpn/list.do"
        )

        /**
         * 접근성 서비스 활성화 상태 확인
         */
        fun isServiceEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${OpinionAutoInputService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(expectedServiceName) == true
        }
    }

    private val captchaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CaptchaDialogActivity.ACTION_CAPTCHA_RESULT) {
                val choice = intent.getStringExtra(CaptchaDialogActivity.EXTRA_CHOICE)
                    ?: PreferenceHelper.CHOICE_CONS

                log("사용자 선택 완료: $choice")

                // 잠시 대기 후 입력 시작
                handler.postDelayed({
                    fillFormAndSubmit(choice)  // captchaValue 파라미터 없이 호출
                }, 500)
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        prefHelper = PreferenceHelper(this)

        // 브로드캐스트 리시버 등록
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(captchaReceiver, IntentFilter(CaptchaDialogActivity.ACTION_CAPTCHA_RESULT))

        log("서비스 시작됨")
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(captchaReceiver)
        log("서비스 종료됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val url = getCurrentUrl() ?: return

        // 의견등록 페이지인 경우
        if (isInsertPage(url)) {
            // 이미 처리한 페이지면 무시
            if (processedPageUrl == url) {
                log("⏭️ 이미 처리한 페이지: $url")
                return
            }

            if (!isProcessing) {
                log("✅ 의견등록 페이지 감지: $url")
                isProcessing = true
                captchaRetryCount = 0
                processedPageUrl = url  // 👈 현재 URL 기억
            }

            // 페이지 콘텐츠 변화 감지
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                onPageContentChanged()
            }
        }
        // 목록 페이지나 다른 페이지로 이동 시 초기화
        else {
            if (processedPageUrl != null) {
                log("🔄 다른 페이지로 이동 - 상태 초기화")
                processedPageUrl = null
                isProcessing = false
                captchaRetryCount = 0
            }

            // 목록 페이지 도달 시 브라우저 닫기
            if (isListPage(url)) {
                handler.postDelayed({
                    closeBrowser()
                }, 500)
            }
        }
    }
    /**
     * 페이지 콘텐츠 변화 감지
     */
    private fun onPageContentChanged() {
        lastContentChangeTime = System.currentTimeMillis()

        // 기존 체크 취소
        pageStabilityCheckRunnable?.let { handler.removeCallbacks(it) }

        // 새로운 안정화 체크 예약
        pageStabilityCheckRunnable = Runnable {
            val timeSinceLastChange = System.currentTimeMillis() - lastContentChangeTime

            if (timeSinceLastChange >= STABILITY_DELAY) {
                // 페이지가 안정화됨
                log("📄 페이지 로딩 완료 (${timeSinceLastChange}ms 동안 변화 없음)")
                detectAndShowCaptcha()
            } else {
                // 아직 변화 중 - 재체크
                log("⏳ 페이지 로딩 중... (마지막 변화: ${timeSinceLastChange}ms 전)")
                handler.postDelayed(pageStabilityCheckRunnable!!, STABILITY_DELAY)
            }
        }

        handler.postDelayed(pageStabilityCheckRunnable!!, STABILITY_DELAY)
    }
    override fun onInterrupt() {
        log("서비스 중단됨")
    }

    /**
     * 현재 URL 가져오기
     */
    private fun getCurrentUrl(): String? {
        val rootNode = rootInActiveWindow ?: return null

        // URL 바 찾기 (브라우저마다 다를 수 있음)
        val urlNode = findNodeByResourceId(rootNode, "com.android.chrome:id/url_bar")
            ?: findNodeByResourceId(rootNode, "com.sec.android.app.sbrowser:id/location_bar_edit_text")
            ?: findNodeByText(rootNode, "pal.assembly.go.kr")

        return urlNode?.text?.toString()
    }

    /**
     * 의견등록 페이지인지 확인
     */
    private fun isInsertPage(url: String): Boolean {
        return INSERT_PAGE_PATTERNS.any { url.contains(it) }
    }

    /**
     * 목록 페이지인지 확인
     */
    private fun isListPage(url: String): Boolean {
        return LIST_PAGE_PATTERNS.any { url.contains(it) } && url.contains("lgsltPaId")
    }

    /**
     * 캡차 감지 및 팝업 표시 (전체 노드 트리 분석)
     */
    private fun detectAndShowCaptcha() {
        val rootNode = rootInActiveWindow ?: run {
            log("⚠️ rootNode를 찾을 수 없음")
            retryDetectCaptcha()
            return
        }

        log("========== 입력 필드 분석 시작 ==========")

        // 모든 노드 수집
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, allNodes)

        // EditText 노드 (입력창)
        val editNodes = allNodes.filter {
            it.className?.toString()?.contains("EditText", ignoreCase = true) == true
        }

        log("✏️ EditText 노드 개수: ${editNodes.size} (시도 ${captchaRetryCount + 1}/$MAX_RETRY)")

        editNodes.forEachIndexed { index, node ->
            log("입력[$index]:")
            log("  contentDesc: ${node.contentDescription}")
            log("  text: ${node.text}")
            log("  viewId: ${node.viewIdResourceName}")
            log("---")
        }

        // 최소 3개의 입력창이 있어야 함 (제목, 본문, 캡차)
        if (editNodes.size < 3) {
            log("❌ 입력창 부족 (${editNodes.size}/3) - 재시도")
            retryDetectCaptcha()
            return
        }

        log("✅ 입력창 ${editNodes.size}개 발견 - 폼 준비 완료!")
        captchaRetryCount = 0
        pageStabilityCheckRunnable?.let { handler.removeCallbacks(it) }

        // 캡차 다이얼로그 표시 (URL은 필요 없지만 호환성 유지)
        val captchaUrl = "https://pal.assembly.go.kr/cmmn/captcha/image.do"
        log("💡 사용자가 브라우저에서 직접 캡차를 볼 수 있습니다")

        // 캡차 다이얼로그 표시
        showCaptchaDialog(captchaUrl)
    }
    /**
     * 모든 노드 재귀적으로 수집
     */
    private fun collectAllNodes(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        result.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllNodes(child, result)
        }
    }

    /**
     * 캡차 감지 재시도
     */
    private fun retryDetectCaptcha() {
        if (captchaRetryCount >= MAX_RETRY) {
            log("❌ 캡차 감지 최대 재시도 횟수 초과 - 중단")
            isProcessing = false
            processedPageUrl = null  // 👈 추가: 실패 시 초기화
            captchaRetryCount = 0
            pageStabilityCheckRunnable?.let { handler.removeCallbacks(it) }
            return
        }

        captchaRetryCount++
        val delayMs = 600L
        log("⏳ ${delayMs}ms 후 재시도 (${captchaRetryCount}/$MAX_RETRY)...")

        handler.postDelayed({
            detectAndShowCaptcha()
        }, delayMs)
    }

    /**
     * 캡차 이미지 노드 찾기
     */
    private fun findCaptchaImage(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 이미지 노드 찾기 (다양한 방법 시도)
        var node = findNodeByContentDescription(rootNode, "보안문자")
        if (node != null) return node

        node = findNodeByResourceId(rootNode, "captchaImg")
        if (node != null) return node

        // 모든 ImageView 검색
        return findNodesByClassName(rootNode, "android.widget.ImageView")
            .firstOrNull { it.contentDescription?.contains("captcha", true) == true }
    }

    /**
     * 이미지 URL 추출
     */
    private fun extractImageUrl(node: AccessibilityNodeInfo): String? {
        // contentDescription에서 URL 추출 시도
        val desc = node.contentDescription?.toString()
        if (desc != null && desc.startsWith("http")) {
            return desc
        }

        // 부모 노드에서 URL 찾기
        var parent = node.parent
        while (parent != null) {
            val parentDesc = parent.contentDescription?.toString()
            if (parentDesc != null && parentDesc.startsWith("http")) {
                return parentDesc
            }
            parent = parent.parent
        }

        // 기본 캡차 URL (실제로는 페이지에서 동적으로 가져와야 함)
        return "https://pal.assembly.go.kr/captcha.do"
    }

    /**
     * 캡차 다이얼로그 표시
     */
    private fun showCaptchaDialog(imageUrl: String) {
        val intent = Intent(this, CaptchaDialogActivity::class.java).apply {
            putExtra(CaptchaDialogActivity.EXTRA_CAPTCHA_URL, imageUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    /**
     * 폼 채우기 (캡차는 사용자가 직접 입력)
     */
    private fun fillFormAndSubmit(choice: String) {
        val rootNode = rootInActiveWindow ?: run {
            log("rootNode를 찾을 수 없음")
            // isProcessing = false 제거!
            return
        }

        // 의견 텍스트 생성
        val texts = prefHelper.makeTexts(choice)

        log("========== 폼 채우기 시작 ==========")
        log("선택: $choice")
        log("제목: ${texts.title}")
        log("본문: ${texts.body}")

        // 모든 EditText 찾기
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(rootNode, allNodes)

        val editNodes = allNodes.filter {
            it.className?.toString()?.contains("EditText", ignoreCase = true) == true
        }

        log("발견된 입력창: ${editNodes.size}개")

        if (editNodes.size < 3) {
            log("❌ 입력창 부족 (${editNodes.size}/3)")
            // isProcessing = false 제거!
            return
        }

        // 제목 입력 (첫 번째 EditText)
        val titleNode = editNodes.getOrNull(0)
        if (titleNode != null) {
            setTextToNode(titleNode, texts.title)
            log("✅ 제목 입력 완료: ${texts.title}")
        }

        // 본문 입력 (두 번째 EditText)
        val bodyNode = editNodes.getOrNull(1)
        if (bodyNode != null) {
            setTextToNode(bodyNode, texts.body)
            log("✅ 본문 입력 완료")
        }

        // 캡차 입력창에 포커스 (세 번째 EditText)
        handler.postDelayed({
            val captchaNode = editNodes.getOrNull(2)
            if (captchaNode != null) {
                captchaNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                captchaNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                log("✅ 캡차 입력창에 포커스 설정 완료")
                log("💡 사용자가 브라우저에서 직접 캡차를 입력해야 합니다")
            } else {
                log("⚠️ 캡차 입력란 없음")
            }

            // 다이얼로그 닫기
            closeCaptchaDialog()

            // isProcessing = false 제거! (URL로 관리)

            log("========== 폼 채우기 끝 ==========")
        }, 300)
    }

    /**
     * 캡차 다이얼로그 닫기
     */
    private fun closeCaptchaDialog() {
        val intent = Intent(CaptchaDialogActivity.ACTION_CLOSE_DIALOG)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /**
     * 브라우저 닫기
     */
    private fun closeBrowser() {
        log("브라우저 닫기 시도")
        performGlobalAction(GLOBAL_ACTION_BACK)

        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
        }, 300)
    }

    // ===== 유틸리티 함수들 =====

    private fun findNodeByResourceId(rootNode: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        if (rootNode.viewIdResourceName?.contains(resourceId) == true) {
            return rootNode
        }
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val found = findNodeByResourceId(child, resourceId)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByText(rootNode: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (rootNode.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return rootNode
        }
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByContentDescription(rootNode: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (rootNode.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) {
            return rootNode
        }
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val found = findNodeByContentDescription(child, desc)
            if (found != null) return found
        }
        return null
    }

    private fun findNodesByClassName(rootNode: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (rootNode.className?.toString() == className) {
            result.add(rootNode)
        }
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            result.addAll(findNodesByClassName(child, className))
        }
        return result
    }

    private fun findInputByText(rootNode: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        val nodes = findNodesByClassName(rootNode, "android.widget.EditText")
        return nodes.firstOrNull {
            node->node.text?.toString()?.contains(hint, ignoreCase = true) == true
        }
    }

    private fun findButtonByText(rootNode: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (rootNode.className?.toString()?.contains("Button") == true &&
            rootNode.text?.toString()?.contains(text, true) == true) {
            return rootNode
        }
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val found = findButtonByText(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun setTextToNode(node: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        // 포커스 주기
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }
}