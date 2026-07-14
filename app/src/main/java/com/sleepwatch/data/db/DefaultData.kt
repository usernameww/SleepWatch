package com.sleepwatch.data.db

import com.sleepwatch.data.db.entity.AlertMessage

object DefaultData {
    val alertMessages = listOf(
        AlertMessage(
            level = 1,
            title = "温和提醒",
            content = "已经过了您设定的就寝时间了，建议您准备休息。",
            healthTip = "规律作息有助于提高睡眠质量",
            isEnabled = true
        ),
        AlertMessage(
            level = 2,
            title = "关切提醒",
            content = "夜深了，充足的睡眠对您的健康很重要，请放下手机吧。",
            healthTip = "睡前使用手机会抑制褪黑素分泌，影响入睡",
            isEnabled = true
        ),
        AlertMessage(
            level = 3,
            title = "严肃提醒",
            content = "您已经在手机上花费了太多时间，睡眠不足会影响明天的精力和效率。",
            healthTip = "成年人每天需要7-9小时的睡眠",
            isEnabled = true
        ),
        AlertMessage(
            level = 4,
            title = "警告提醒",
            content = "长期熬夜会增加心血管疾病、肥胖和抑郁的风险，请立即休息！",
            healthTip = "熬夜会导致免疫力下降，增加患病风险",
            isEnabled = true
        ),
        AlertMessage(
            level = 5,
            title = "最强提醒",
            content = "这是今晚最后一次提醒！为了您的健康，请立刻放下手机入睡！",
            healthTip = "良好的睡眠是健康的基础，今晚早睡，明天更好",
            isEnabled = true
        )
    )
}
