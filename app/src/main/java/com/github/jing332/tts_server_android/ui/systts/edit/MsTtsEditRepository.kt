package com.github.jing332.tts_server_android.ui.systts.edit

import com.github.jing332.tts_server_android.App
import com.github.jing332.tts_server_android.app
import com.github.jing332.tts_server_android.bean.AzureVoiceBean
import com.github.jing332.tts_server_android.bean.CreationVoiceBean
import com.github.jing332.tts_server_android.bean.EdgeVoiceBean
import com.github.jing332.tts_server_android.constant.CnLocalMap
import com.github.jing332.tts_server_android.constant.MsTtsApiType
import com.github.jing332.tts_server_android.util.FileUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import tts_server_lib.Tts_server_lib
import java.io.File

class MsTtsEditRepository(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private val json by lazy { App.jsonBuilder }
        private val EDGE_CACHE_PATH by lazy { "${app.cacheDir.path}/edge/voices.json" }
        private val AZURE_CACHE_PATH by lazy { "${app.cacheDir.path}/azure/voices.json" }
        private val CREATION_CACHE_PATH by lazy { "${app.cacheDir.path}/creation/voices.json" }
    }

    /**
     * 根据api获取数据
     */
    suspend fun voicesByApi(@MsTtsApiType api: Int): List<GeneralVoiceData> {
        return withContext(defaultDispatcher) {
            return@withContext when (api) {
                MsTtsApiType.EDGE -> edgeVoices()
                MsTtsApiType.AZURE -> azureVoices()
                else -> creationVoices()
            }
        }
    }

    // 帮助 获取并解析数据
    private inline fun <reified T> getVoicesHelper(
        cachePath: String,
        loadData: () -> ByteArray
    ): List<T> {
        val file = File(cachePath)
        val voiceList: List<T> = if (FileUtils.fileExists(file)) {
            json.decodeFromString(file.readText())
        } else {
            val data = loadData.invoke()
            FileUtils.saveFile(file, data)
            json.decodeFromString(data.decodeToString())
        }
        return voiceList
    }

    private fun edgeVoices(): List<GeneralVoiceData> {
        val list = getVoicesHelper<EdgeVoiceBean>(EDGE_CACHE_PATH) {
            Tts_server_lib.getEdgeVoices()
        }

        return list.map {
            GeneralVoiceData(
                gender = it.gender,
                locale = it.locale,
                voiceName = it.shortName
            )
        }
    }

    private fun azureVoices(): List<GeneralVoiceData> {
        val list = getVoicesHelper<AzureVoiceBean>(AZURE_CACHE_PATH) {
            Tts_server_lib.getAzureVoice()
        }
        return list.map {
            GeneralVoiceData(
                gender = it.gender,
                locale = it.locale, voiceName = it.shortName,
                _styles = it.styleList, _roles = it.rolePlayList,
                _secondaryLocales = it.secondaryLocaleList
            )
        }
    }

    private fun creationVoices(): List<GeneralVoiceData> {
        val list = getVoicesHelper<CreationVoiceBean>(CREATION_CACHE_PATH) {
            Tts_server_lib.getCreationVoices()
        }
        return list.map {
            GeneralVoiceData(
                gender = it.properties.gender,
                voiceId = it.id,
                voiceName = it.shortName,
                locale = it.locale,
                _localVoiceName = it.properties.localName,
                _styles = it.properties.voiceStyleNames,
                _roles = it.properties.voiceRoleNames,
                _secondaryLocales = it.properties.SecondaryLocales
            )
        }
    }

}


data class GeneralVoiceData(
    /**
     * 性别 Male:男, Female:女
     */
    val gender: String,
    /**
     * UUID 仅Creation
     */
    val voiceId: String? = null,
    /**
     * 地区代码 zh-CN
     */
    val locale: String,
    /**
     * Voice zh-CN-XiaoxiaoNeural
     */
    val voiceName: String,
    /**
     * 本地化发音人名称 晓晓
     */
    private val _localVoiceName: String? = null,

    // 二级语言（语言技能） 仅限 en-US-JennyMultilingualNeural
    private val _secondaryLocales: Any? = null,

    // 风格列表 azure为List<String>, Creation为string 逗号分割
    private
    val _styles: Any? = null,
    // 角色列表
    private val _roles: Any? = null,
) {
    /*
    * 是否为男发音
    * */
    val isMale: Boolean
        get() = gender == "Male"

    /**
     * 汉化的地区名
     */
    val localeName: String
        get() = CnLocalMap.getLanguage(locale)

    /**
     * 获取发音人本地化名称，edge则为汉化
     */
    val localVoiceName: String
        get() {
            _localVoiceName?.let { return _localVoiceName }
            return CnLocalMap.getEdgeVoice(voiceName)
        }

    /**
     * 获取风格列表
     */
    val styleList: List<String>?
        get() = transformToList(_styles)

    /**
     * 获取汉化的风格列表
     * @return first: 原Key, second: 汉化Value
     */
    val localStyleList: List<Pair<String, String>>?
        get() = styleList?.map { Pair(it, CnLocalMap.getStyleAndRole(it)) }
            ?.also { if (it.isEmpty()) return null }

    /**
     * 获取角色列表
     */
    val roleList: List<String>?
        get() = transformToList(_roles)

    /**
     * 获取汉化的角色列表
     * @return first: 原Key, second: 汉化Value
     */
    val localRoleList: List<Pair<String, String>>?
        get() = roleList?.map { Pair(it, CnLocalMap.getStyleAndRole(it)) }
            ?.also { if (it.isEmpty()) return null }

    /**
     * 二级语言列表
     */
    val secondaryLocaleList: List<String>?
        get() = transformToList(_secondaryLocales)

    /**
     * 汉化的二级语言列表
     * @return first: 原Key, second: 汉化Value
     */
    val localSecondaryLocaleList: List<Pair<String, String>>?
        get() = secondaryLocaleList?.map { Pair(it, CnLocalMap.getLanguage(it)) }
            ?.also { if (it.isEmpty()) return null }

    // 根据类型(String或List) 自动转换
    private fun transformToList(obj: Any?): List<String>? {
        obj?.let { v ->
            if (v is List<*>) {
                return v.map { it.toString() }
            } else if (v is String) {
                return v.split(",").filter { it.isNotBlank() }
            }
        }
        return null
    }

}