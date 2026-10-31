package com.example.domain

import com.example.viewmodel.SupportedLanguage

fun generateCompanionFallbackAnswer(query: String?, language: SupportedLanguage): String {
    val q = query?.lowercase() ?: ""
    return when {
        q.contains("ቀልድ") || q.contains("joke") || q.contains("qoosaa") -> {
            when (language) {
                SupportedLanguage.ENGLISH -> "Why don't scientists trust atoms? Because they make up everything! 😄 What else would you like to hear?"
                SupportedLanguage.AFAN_OROMOO -> "Namichi tokko mana kuusaa daldalaa dhaqee 'falaasaa balali'u qabduu?' jennaan daldalaan 'eeyyee garuu boba'aan waan dhumateef lafa irra deema' jedheen! 😄"
                SupportedLanguage.AMHARIC -> "አንድ ሰውዬ ወደ ሱቅ ሄዶ 'የሚበር ምንጣፍ አለ?' ሲል ሻጩ 'አዎ አለ ግን የነዳጅ ዋጋ ስለጨመረ በእግር ነው የሚሄደው' አለው! 😂 ሌላ ቀልድ ወይም መረጃ ይፈልጋሉ?"
            }
        }
        q.contains("እውነታ") || q.contains("fact") || q.contains("aja'ib") -> {
            when (language) {
                SupportedLanguage.ENGLISH -> "Fun Fact: Ethiopia is the birthplace of Coffee Arabica and follows a unique 13-month calendar with 12 months of 30 days and one month of 5 or 6 days! ☕"
                SupportedLanguage.AFAN_OROMOO -> "Waan nama ajaa'ibu: Itoophiyaan biyya addunyaa irratti ji'a 13 qabdu isa tokkicha! Akkasumas madda bunaati. ☕"
                SupportedLanguage.AMHARIC -> "አስደናቂ እውነታ፦ ኢትዮጵያ በዓለም ላይ የራሷ የሆነ 13 ወራት ያላት ብቸኛ ሀገር ናት! በተጨማሪም የዓለማችን ምርጥ የቡና መገኛ የካፋ ምድር ናት። ☕"
            }
        }
        q.contains("ዜና") || q.contains("news") || q.contains("oduu") -> {
            when (language) {
                SupportedLanguage.ENGLISH -> "Today's Update: Advancements in AI assistive vision are empowering visually impaired individuals around the globe. I'm right here as your companion! 📰"
                SupportedLanguage.AFAN_OROMOO -> "Oduu Har'aa: Saayinsiin fi teeknooloojiin AI qaro-dhabeeyyii gargaaruuf daran guddataa jira. Anis yeroo hunda si cinaa jira! 📰"
                SupportedLanguage.AMHARIC -> "የዛሬ የቴክኖሎጂና ዕለታዊ ዜና፦ በዓለም ዙሪያ አዳዲስ የሰው ሰራሽ አስተዋይ (AI) ፈጠራዎች ለዓይነ ስውራን ድጋፍ በመስጠት ላይ ይገኛሉ። እኔም በማንኛውም ሰዓት ከጎንዎ ነኝ! 📰"
            }
        }
        q.contains("ምክር") || q.contains("advice") || q.contains("gorsa") -> {
            when (language) {
                SupportedLanguage.ENGLISH -> "Daily Wisdom: 'A stitch in time saves nine.' Take a deep breath, stay confident, and believe in your steps today! ☀️"
                SupportedLanguage.AFAN_OROMOO -> "Gorsa Guyyaa: Yeroo hunda ofitti amani, carraaqqiin bu'aa gaarii fida! ☀️"
                SupportedLanguage.AMHARIC -> "የዕለቱ ምክር፦ 'ድር ቢያብር አንበሳ ያስር።' ሁልጊዜ በራስ መተማመን ይኑርዎት፤ ዛሬ የተሻለ ቀን እንዲሆንልዎ እመኛለሁ! ☀️"
            }
        }
        else -> {
            when (language) {
                SupportedLanguage.ENGLISH -> "I'm your AI companion! You can chat with me about jokes, daily news, fascinating facts, or any question on your mind."
                SupportedLanguage.AFAN_OROMOO -> "Ani gargaaraa keeti! Qoosaa, oduu haaraa, fi gaaffii kamiyyuu na gaafachuu dandeessa."
                SupportedLanguage.AMHARIC -> "እኔ ረዳትዎ ነኝ! ስለ ቀልድ፣ አስደናቂ እውነታዎች፣ ዜና ወይም ማንኛውም ጥያቄ አብረን ማውራት እንችላለን።"
            }
        }
    }
}
