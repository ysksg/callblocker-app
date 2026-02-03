package net.ysksg.callblocker.util

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import java.util.Locale

object PhoneNumberFormatter {
    private val phoneUtil = PhoneNumberUtil.getInstance()

    /**
     * 電話番号を読みやすい形式にフォーマットする
     * 日本の番号(+81)であれば 090-1234-5678 のような国内形式(NATIONAL)
     * 海外の番号であれば +1 234-567-8900 のような国際形式(INTERNATIONAL)
     */
    fun format(number: String, defaultRegion: String = "JP"): String {
        if (number.isEmpty()) return "非通知"
        try {
            // パースを試みる
            // "+81..." のような国際形式は自動判別される
            // "090..." のような国内形式は defaultRegion (JP) として扱われる
            val numberProto: PhoneNumber = phoneUtil.parse(number, defaultRegion)
            
            // 有効な番号かどうかチェック（厳密すぎる場合があるのでパースできればOKとする思想もあるが、一応）
            // if (!phoneUtil.isValidNumber(numberProto)) return number 

            // 日本の番号(+81)の場合
            if (numberProto.countryCode == 81) {
                // NATIONAL形式: 090-1234-5678
                return phoneUtil.format(numberProto, PhoneNumberFormat.NATIONAL)
            }

            // 海外の番号の場合
            // INTERNATIONAL形式: +1 650-253-0000
            return phoneUtil.format(numberProto, PhoneNumberFormat.INTERNATIONAL)

        } catch (e: Exception) {
            // パース失敗時などは元の文字列を返す
            return number
        }
    }
}
