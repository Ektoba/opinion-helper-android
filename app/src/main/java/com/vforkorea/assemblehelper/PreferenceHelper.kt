package com.vforkorea.assemblehelper

import android.content.Context
import android.content.SharedPreferences

class PreferenceHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("opinion_helper_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_CHOICE = "last_choice"
        const val CHOICE_CONS = "cons"  // 반대
        const val CHOICE_PROS = "pros"  // 찬성
    }

    /**
     * 마지막 선택값 저장 (찬성/반대)
     */
    fun saveLastChoice(choice: String) {
        prefs.edit().putString(KEY_LAST_CHOICE, choice).apply()
    }

    /**
     * 마지막 선택값 가져오기 (기본값: 반대)
     */
    fun getLastChoice(): String {
        return prefs.getString(KEY_LAST_CHOICE, CHOICE_CONS) ?: CHOICE_CONS
    }

    /**
     * 선택값에 따른 텍스트 생성
     */
    fun makeTexts(choice: String): OpinionTexts {
        return if (choice == CHOICE_PROS) {
            OpinionTexts(
                title = "찬성합니다",
                body = "발의된 이 법안에 찬성합니다. 조속한 통과를 요청드립니다."
            )
        } else {
            OpinionTexts(
                title = "반대합니다",
                body = "발의된 이 법안에 반대합니다. 충분한 재검토를 요청드립니다."
            )
        }
    }

    data class OpinionTexts(
        val title: String,
        val body: String
    )
}