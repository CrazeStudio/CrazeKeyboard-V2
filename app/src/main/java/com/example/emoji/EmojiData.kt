package com.example.emoji

import com.example.R

enum class EmojiCategory(val title: String, val iconResId: Int) {
    RECENT("Recent", R.drawable.ic_emoji_recent),
    SMILEYS("Smileys", R.drawable.ic_emoji_smile),
    PEOPLE("People", R.drawable.ic_emoji_people),
    NATURE("Nature", R.drawable.ic_emoji_nature),
    FOOD("Food", R.drawable.ic_emoji_food),
    ACTIVITIES("Activities", R.drawable.ic_emoji_activities),
    TRAVEL("Travel", R.drawable.ic_emoji_travel),
    OBJECTS("Objects", R.drawable.ic_emoji_object),
    SYMBOLS("Symbols", R.drawable.ic_emoji_symbol),
    FLAGS("Flags", R.drawable.ic_emoji_flags)
}

object EmojiData {
    val smileysList = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹", "☺️", "😊", "😇", "🙂", "🙃", "😉",
        "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎",
        "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺",
        "😢", "😭", "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓",
        "🤗", "🤔", "🫣", "🤭", "🫢", "🫡", "🤫", "🫠", "🤥", "😶", "😶‍🌫️", "😐", "😑", "😬", "🫨", "😮",
        "😯", "😲", "🥱", "😴", "🤤", "😪", "😵", "😵‍💫", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕",
        "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃"
    )

    val peopleList = listOf(
        "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟",
        "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌",
        "🫶", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃",
        "🫀", "🫁", "🧠", "🦷", "🦴", "👀", "👁️", "👅", "👄", "🫦", "👶", "🧒", "👦", "👧", "🧑", "👱",
        "👨", "🧔", "🧔‍♂️", "👩", "🧓", "👴", "👵", "👲", "🧕", "👮", "👷", "💂", "🕵️", "🧑‍⚕️", "🧑‍🌾", "🧑‍🍳"
    )

    val natureList = listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸",
        "🐵", "🐒", "🦍", "🦧", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗",
        "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌", "🐞", "🐜", "🪰", "🪲", "🪳", "🪴", "🌲", "🌳", "🌴",
        "🌵", "🌾", "🌿", "☘️", "🍀", "🍁", "🍂", "🍃", "🍄", "🪨", "🌸", "💮", "🪷", "🏵️", "🌹", "🥀",
        "🌺", "🌻", "🌼", "🌷", "🌱", "☀️", "🌤️", "⛅", "🌥️", "☁️", "🌦️", "🌧️", "⛈️", "🌩️", "⚡", "❄️",
        "☃️", "⛄", "🌬️", "💨", "🌪️", "🌫️", "🌈", "⭐", "🌟", "✨", "💫", "☄️", "🌙", "🌕", "🌖", "🌑"
    )

    val foodList = listOf(
        "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥",
        "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🫑", "🌽", "🥕", "🫒", "🧄", "🧅", "🥔", "🍠",
        "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🦴",
        "🌭", "🍔", "🍟", "🍕", "🫓", "🥪", "🥙", "🧆", "🌮", "🌯", "🫔", "🥗", "🥘", "🫕", "🍲", "🍝",
        "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🦪", "🍤", "🍙", "🍚", "🍘", "🍢", "🥮", "🍡", "🍧", "🍨",
        "🍦", "🥧", "🧁", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "🌰", "🥜", "🍯", "🥛",
        "☕", "🫖", "🍵", "🧃", "🥤", "🧋", "🍶", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹", "🧉", "🍾"
    )

    val activitiesList = listOf(
        "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
        "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️", "🥌",
        "🎿", "⛷️", "🏂", "🏋️", "🤼", "🤸", "🤺", "⛹️", "🤾", "🧗", "🧘", "🏇", "🏄", "🏊", "🤽", "🚣",
        "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🎫", "🎟️", "🎪", "🤹", "🎭", "🩰", "🎨",
        "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🪘", "🎷", "🎺", "🪗", "🎸", "🪕", "🎻", "🎲", "♟️", "🎯",
        "🎳", "🎮", "🕹️", "🎰"
    )

    val travelList = listOf(
        "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🦯", "🦽",
        "🦼", "🛴", "🚲", "🛵", "🏍️", "🛺", "🚨", "🚔", "🚍", "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋",
        "🚞", "🚝", "🚄", "🚅", "🚈", "🚂", "🚆", "🚇", "🚊", "🚉", "✈️", "🛫", "🛬", "🛩️", "💺", "🛰️",
        "🚀", "🛸", "🚁", "🛶", "⛵", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓", "🛟", "🪝", "⛽", "🚧", "🚦",
        "🚥", "🛑", "🗺️", "🗿", "🗽", "🗼", "🏰", "🏯", "🏟️", "🎡", "🎢", "🏖️", "🏝️", "🏜️", "🌋", "⛰️"
    )

    val objectsList = listOf(
        "💻", "🖥️", "🖨️", "⌨️", "🖱️", "🖲️", "💽", "💾", "💿", "📀", "📱", "📲", "☎️", "📞", "📟", "📠",
        "🔋", "🪫", "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️", "💸", "💵", "💴", "💶", "💷", "🪙", "💰",
        "💳", "💎", "⚖️", "🪜", "🧰", "🪛", "🔧", "🔨", "⚒️", "🛠️", "⛏️", "🪚", "🔩", "⚙️", "🪤", "🧱",
        "⛓️", "🧲", "🔫", "💣", "🧨", "🪓", "🔪", "🗡️", "⚔️", "🛡️", "🚬", "⚰️", "🪦", "⚱️", "🏺", "🔮",
        "📿", "🧿", "💈", "🔭", "🔬", "🕳️", "🩹", "🩺", "💊", "💉", "🩸", "🧬", "🦠", "🧫", "🧪", "🌡️",
        "🧹", "🪠", "🧺", "🧻", "🚽", "🚰", "🚿", "🛁", "🧼", "🪥", "🪒", "🧽", "🪣", "🧴", "🔑", "🗝️",
        "🚪", "🪑", "🛋️", "🛏️", "🛌", "🧸", "🪆", "🖼️", "🪞", "🪟", "🛍️", "🛒", "🎁", "🎈", "🎏", "🎀",
        "🪄", "🪅", "🎊", "🎉", "🎉", "🎎", "🏮", "🎐", "🧧", "✉️", "📩", "📨", "📧", "💌", "📦", "🏷️",
        "📄", "📃", "📑", "📊", "📈", "📉", "📜", "📝", "📋", "📁", "📂", "🗂️", "📅", "📆", "🗓️", "📇"
    )

    val symbolsList = listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓",
        "💗", "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐",
        "⛎", "♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐", "♑", "♒", "♓", "🆔", "⚛️", "🉑",
        "☢️", "☣️", "📴", "📳", "🈶", "🈚", "🈸", "🈺", "🈷️", "✴️", "VS", "🉐", "㊙️", "㊗️", "🈴", "🈵",
        "💯", "🔘", "🟢", "🟡", "🟠", "🔴", "🟣", "🟤", "⚫", "⚪", "🟥", "🟧", "🟨", "🟩", "🟦", "🟪",
        "🟫", "⬛", "⬜", "🔷", "🔶", "🔹", "🔸", "🔺", "🔻", "💠", "🔘", "🔳", "🔲", "🏁", "🚩", "🎌",
        "🏴", "🏳️", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️", "🔞", "⛔", "📛", "🚫", "❌", "⭕", "🛑", "💢", "♨️", "🚷", "🚯",
        "🚳", "🚱", "🔞", "📵", "🚭", "❗", "❕", "❓", "❔", "‼️", "⁉️", "🔅", "🔆", "〽️", "⚠️", "🚸"
    )

    val flagsList = listOf(
        "🏁", "🚩", "🎌", "🏴", "🏳️", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️", "🇺🇸", "🇬🇧", "🇨🇦", "🇦🇺", "🇩🇪", "🇫🇷", "🇮🇹", "🇪🇸",
        "🇯🇵", "🇰🇷", "🇨🇳", "🇮🇳", "🇧🇷", "🇲🇽", "🇷🇺", "🇿🇦", "🇸🇦", "🇦🇪", "🇹🇷", "🇧🇩", "🇵🇰", "🇮🇩", "🇳🇬", "🇪🇬",
        "🇦🇷", "🇨🇱", "🇨🇴", "🇳🇱", "🇸🇪", "🇳🇴", "🇩🇰", "🇫🇮", "🇵🇱", "🇺🇦", "🇬🇷", "🇵🇹", "🇨🇭", "🇦🇹", "🇧🇪", "🇳🇿",
        "🇸🇬", "🇲🇾", "🇹🇭", "🇻🇳", "🇵🇭", "🇮🇪", "🇮🇱", "🇯🇲", "🇨🇺", "🇵🇷", "🇭🇰", "🇹🇼"
    )

    val defaultRecent = listOf(
        "👍", "❤️", "😊", "🔥", "✨", "🎉", "🚀", "💻", "⚡", "🙌", "👏", "💡", "🤖", "✅", "😍", "🤣", "🎂", "🥳", "☕", "🍕"
    )

    val allEmojis: List<String> by lazy {
        (smileysList + peopleList + natureList + foodList + activitiesList + travelList + objectsList + symbolsList + flagsList).distinct()
    }

    private val emojiKeywords: Map<String, List<String>> = mapOf(
        "😀" to listOf("grinning", "happy", "smile", "joy", "laugh", "cheerful"),
        "😃" to listOf("grinning", "big eyes", "happy", "smile", "joy", "cheerful"),
        "😄" to listOf("smile", "happy", "laugh", "eyes", "joy", "cheerful"),
        "😁" to listOf("beam", "smile", "happy", "teeth", "grin"),
        "😆" to listOf("squint", "laugh", "happy", "funny", "haha", "lol"),
        "😅" to listOf("sweat", "nervous", "relief", "smile", "whew"),
        "😂" to listOf("joy", "tears", "laugh", "funny", "lol", "lmao", "rofl", "crying"),
        "🤣" to listOf("rofl", "rolling", "laugh", "floor", "funny", "lol", "lmao"),
        "🥲" to listOf("grateful", "cry", "happy", "proud", "tears"),
        "🥹" to listOf("pleading", "holding tears", "emotional", "proud", "touching"),
        "☺️" to listOf("warm", "smile", "happy", "blush", "relaxed"),
        "😊" to listOf("blush", "happy", "smile", "cute", "warm"),
        "😇" to listOf("angel", "halo", "innocent", "good", "holy"),
        "🙂" to listOf("slight smile", "fine", "ok", "okay"),
        "🙃" to listOf("upside down", "sarcastic", "goofy", "silly"),
        "😉" to listOf("wink", "flirt", "joke", "secret"),
        "😌" to listOf("relieved", "calm", "peace", "zen"),
        "😍" to listOf("heart eyes", "love", "crush", "gorgeous", "adore", "romantic"),
        "🥰" to listOf("hearts", "love", "cute", "affection", "in love"),
        "😘" to listOf("kiss", "blow kiss", "love", "flirt"),
        "😋" to listOf("yum", "delicious", "tasty", "tongue", "food"),
        "😛" to listOf("tongue", "silly", "playful", "joke"),
        "😜" to listOf("wink", "tongue", "party", "crazy", "joke"),
        "🤪" to listOf("zany", "crazy", "wild", "party", "goofy"),
        "🤨" to listOf("raised eyebrow", "skeptical", "suspicious", "really"),
        "🧐" to listOf("monocle", "curious", "investigate", "detective", "smart"),
        "🤓" to listOf("nerd", "geek", "smart", "glasses", "code"),
        "😎" to listOf("cool", "sunglasses", "boss", "awesome", "slick"),
        "🥳" to listOf("party", "celebration", "birthday", "yay", "congrats", "cheers"),
        "😏" to listOf("smirk", "flirt", "knowing", "sly"),
        "😒" to listOf("unamused", "annoyed", "bored", "meh"),
        "😞" to listOf("disappointed", "sad", "unhappy"),
        "😔" to listOf("pensive", "sad", "sorry", "down"),
        "😟" to listOf("worried", "anxious", "nervous"),
        "😕" to listOf("confused", "unsure", "hesitant"),
        "🙁" to listOf("slight frown", "sad"),
        "☹️" to listOf("frown", "sad", "unhappy"),
        "🥺" to listOf("pleading", "begging", "puppy eyes", "please", "cute"),
        "😢" to listOf("cry", "sad", "tear", "unhappy"),
        "😭" to listOf("loud cry", "sobbing", "sad", "tears", "omg"),
        "😤" to listOf("triumph", "proud", "frustrated", "steam"),
        "😠" to listOf("angry", "mad", "annoyed"),
        "😡" to listOf("pouting", "rage", "red angry", "furious"),
        "🤬" to listOf("cursing", "swearing", "furious", "mad"),
        "🤯" to listOf("mind blown", "exploding head", "insane", "shocked", "wow"),
        "😳" to listOf("flushed", "embarrassed", "shocked", "blush"),
        "🥵" to listOf("hot", "heat", "sweat", "summer", "spicy"),
        "🥶" to listOf("cold", "freezing", "ice", "winter"),
        "😱" to listOf("scream", "scared", "shocked", "horror", "omg"),
        "😨" to listOf("fearful", "scared", "afraid"),
        "😰" to listOf("anxious", "nervous", "sweat"),
        "😥" to listOf("sad", "relieved", "worried"),
        "😓" to listOf("downcast", "sweat", "tired"),
        "🤗" to listOf("hug", "warm", "welcome", "open arms"),
        "🤔" to listOf("thinking", "hmm", "ponder", "wonder", "curious"),
        "🫣" to listOf("peeking", "shy", "scared", "cant look"),
        "🤭" to listOf("hand over mouth", "giggle", "oops", "secret"),
        "🫢" to listOf("open eyes", "gasped", "shock", "surprise"),
        "🫡" to listOf("salute", "respect", "yes sir", "understood"),
        "🤫" to listOf("shh", "quiet", "silence", "secret"),
        "🫠" to listOf("melting", "hot", "embarrassed", "sinking"),
        "🤥" to listOf("lying", "pinocchio", "fake", "liar"),
        "😶" to listOf("speechless", "no mouth", "quiet"),
        "😐" to listOf("neutral", "poker face", "meh"),
        "😑" to listOf("expressionless", "done", "tired"),
        "😬" to listOf("grimace", "awkward", "oops", "nervous"),
        "🫨" to listOf("shaking", "earthquake", "dizzy", "shock"),
        "😮" to listOf("open mouth", "wow", "surprise", "gasp"),
        "🥱" to listOf("yawn", "tired", "sleepy", "bored"),
        "😴" to listOf("sleeping", "sleep", "zzz", "bed", "goodnight"),
        "🤤" to listOf("drool", "yummy", "hungry", "delicious"),
        "😵" to listOf("dizzy", "knocked out", "dead"),
        "🤢" to listOf("nauseated", "sick", "gross"),
        "🤮" to listOf("vomit", "barf", "disgusted", "sick"),
        "🤧" to listOf("sneeze", "cold", "sick", "allergy"),
        "😷" to listOf("mask", "medical", "doctor", "sick", "virus"),
        "🤒" to listOf("fever", "thermometer", "sick", "ill"),
        "🤕" to listOf("bandage", "hurt", "injury", "headache"),
        "🤑" to listOf("money", "rich", "dollar", "cash", "profit"),
        "🤠" to listOf("cowboy", "hat", "western", "yeehaw"),
        "😈" to listOf("devil", "evil", "smile", "naughty"),
        "👿" to listOf("angry devil", "evil", "demon"),
        "🤡" to listOf("clown", "joke", "fool"),
        "💩" to listOf("poop", "poo", "crap", "funny"),
        "👻" to listOf("ghost", "spooky", "halloween", "boo"),
        "💀" to listOf("skull", "dead", "dying", "skeleton", "lol"),
        "☠️" to listOf("crossbones", "danger", "pirate", "poison"),
        "👽" to listOf("alien", "ufo", "space", "sci fi"),
        "🤖" to listOf("robot", "bot", "ai", "tech", "machine"),
        "🎃" to listOf("pumpkin", "jack o lantern", "halloween"),
        
        "👍" to listOf("thumbs up", "like", "approve", "good", "agree", "yes", "ok"),
        "👎" to listOf("thumbs down", "dislike", "bad", "no", "disagree"),
        "👏" to listOf("clap", "applause", "bravo", "good job", "congrats"),
        "🙌" to listOf("raising hands", "praise", "celebrate", "woohoo"),
        "🫶" to listOf("heart hands", "love", "care", "support"),
        "🙏" to listOf("pray", "please", "thanks", "thank you", "hope", "namaste"),
        "🤝" to listOf("handshake", "deal", "agree", "partnership"),
        "✌️" to listOf("peace", "v sign", "victory"),
        "🤞" to listOf("fingers crossed", "luck", "hopeful"),
        "🤟" to listOf("love you", "rock on", "sign"),
        "🤘" to listOf("rock", "metal", "heavy metal"),
        "🤙" to listOf("call me", "shaka", "hang loose"),
        "👌" to listOf("ok", "perfect", "okay", "fine"),
        "🤌" to listOf("pinched fingers", "italian", "chef", "what"),
        "🤏" to listOf("pinching", "small", "tiny", "little bit"),
        "💪" to listOf("muscle", "strong", "flex", "workout", "gym", "power"),
        "🔥" to listOf("fire", "lit", "hot", "flame", "burn", "awesome"),
        "✨" to listOf("sparkles", "shine", "magic", "clean", "glitter", "star"),
        "🎉" to listOf("party popper", "celebrate", "tada", "congrats", "birthday", "yay"),
        "🚀" to listOf("rocket", "launch", "moon", "fast", "space", "crypto", "speed"),
        "💡" to listOf("light bulb", "idea", "smart", "solution", "innovate"),
        "💻" to listOf("laptop", "computer", "code", "dev", "tech", "work"),
        "📱" to listOf("phone", "mobile", "smartphone", "iphone", "android"),
        "❤️" to listOf("red heart", "love", "passion", "favorite"),
        "💖" to listOf("sparkling heart", "love", "sweet"),
        "💔" to listOf("broken heart", "sad", "breakup", "hurt"),
        "💯" to listOf("100", "hundred", "perfect", "score", "real"),
        "✅" to listOf("check", "done", "complete", "verified", "correct"),
        "☕" to listOf("coffee", "cappuccino", "espresso", "morning", "cafe", "tea"),
        "🍕" to listOf("pizza", "food", "slice", "cheese", "snack"),
        "🍔" to listOf("burger", "hamburger", "fast food", "lunch"),
        "🍟" to listOf("fries", "french fries", "fast food", "potato"),
        "🎂" to listOf("birthday cake", "cake", "party", "bday", "celebrate"),
        "🍻" to listOf("cheers", "beer", "drinks", "bar", "party"),
        "🍷" to listOf("wine", "drinks", "glass", "dinner"),
        "🐶" to listOf("dog", "puppy", "pet", "cute", "bark"),
        "🐱" to listOf("cat", "kitten", "kitty", "pet", "meow"),
        "🚗" to listOf("car", "drive", "ride", "traffic", "road"),
        "✈️" to listOf("airplane", "flight", "travel", "vacation", "trip"),
        "☀️" to listOf("sun", "sunny", "summer", "warm", "bright", "day"),
        "🌧️" to listOf("rain", "rainy", "storm", "cloud", "weather"),
        "⭐" to listOf("star", "favorite", "yellow", "night"),
        "💰" to listOf("money bag", "cash", "dollar", "wealth", "rich")
    )

    fun getCategoryList(category: EmojiCategory, recentList: List<String>): List<String> {
        return when (category) {
            EmojiCategory.RECENT -> if (recentList.isNotEmpty()) recentList else defaultRecent
            EmojiCategory.SMILEYS -> smileysList
            EmojiCategory.PEOPLE -> peopleList
            EmojiCategory.NATURE -> natureList
            EmojiCategory.FOOD -> foodList
            EmojiCategory.ACTIVITIES -> activitiesList
            EmojiCategory.TRAVEL -> travelList
            EmojiCategory.OBJECTS -> objectsList
            EmojiCategory.SYMBOLS -> symbolsList
            EmojiCategory.FLAGS -> flagsList
        }
    }

    fun searchEmojis(query: String): List<String> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return emptyList()
        val queryTokens = cleanQuery.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val results = mutableListOf<String>()
        // 1. Check keyword dictionary matches
        for ((emoji, keywords) in emojiKeywords) {
            val matchesAll = queryTokens.all { token ->
                keywords.any { kw -> kw.contains(token) }
            }
            if (matchesAll) {
                results.add(emoji)
            }
        }
        // 2. Also match directly in case user pastes an emoji or symbol
        for (emoji in allEmojis) {
            if (emoji.contains(cleanQuery) && !results.contains(emoji)) {
                results.add(emoji)
            }
        }
        return results
    }
}
