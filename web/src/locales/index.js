import sysConfig from "@/config"
import tool from '@/utils/tool'
import {createI18n} from 'vue-i18n'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import enCn from 'element-plus/dist/locale/en.mjs'

import zh_cn from './lang/zh-cn.js'
import en from './lang/en.js'

const messages = {
	'zh-cn': {
		el: zhCn,
		...zh_cn
	},
	'en': {
		el: enCn,
		...en
	}
}

const i18n = createI18n({
	locale: tool.data.get("APP_LANG") || sysConfig.LANG,
	fallbackLocale: 'zh-cn',
	globalInjection: true,
	legacy: false, // 使用Composition API模式
	silentTranslationWarn: true, // 静默翻译警告
	silentFallbackWarn: true, // 静默回退警告
	messages,
})

export default i18n;
