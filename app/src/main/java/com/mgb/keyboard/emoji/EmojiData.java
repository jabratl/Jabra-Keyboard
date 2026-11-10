package com.mgb.keyboard.emoji;

/**
 * v0.1.0 emoji lists (pure Java, no Android). Only well-known emoji (mostly Unicode 9 or older) so most phones draw them.
 * To add / remove an emoji: edit the text of one category below (emoji separated by ONE space). Run tools/EmojiTest.java.
 */
public final class EmojiData {
    private EmojiData() { }

    /** Tab icons (one per category, same order as DATA). */
    public static final String[] ICONS = { "\uD83D\uDE00", "\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDC36", "\uD83C\uDF54", "\u26BD", "\uD83D\uDE97", "\uD83D\uDCA1", "\uD83D\uDD23" };

    /** Category names (same order). */
    public static final String[] NAMES = { "Smileys", "Hands", "Hearts", "Animals", "Food", "Activity", "Travel", "Objects", "Symbols" };

    private static final String[] DATA = {
        // Smileys
        "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 😘 😗 😙 😚 😋 😛 😝 😜 🤓 😎 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 😢 😭 😤 😠 😡 😳 😱 😨 😰 😥 😓 🤗 🤔 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 😴 🤤 😪 😵 🤐 🤢 🤧 😷 🤒 🤕 😈 👿 💀 👻 👽 🤖 💩 🙈 🙉 🙊",
        // Hands
        "👍 👎 👌 ✌️ 🤞 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🖖 👋 💪 🙏 ✍️ 👏 🙌 👐 🤝 👊 ✊ 🤛 🤜 🖕 💅 👀 👅 👄 👂 👃 🧠 👶 👦 👧 👨 👩 👴 👵",
        // Hearts
        "❤️ 🧡 💛 💚 💙 💜 🖤 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 💋 💌 💯 💢 💥 💫 💦 💨 💤 🔥 ✨ ⭐ 🌟",
        // Animals
        "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐺 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🐙 🦀 🐠 🐟 🐬 🐳 🐘 🐒 🐕 🐈 🌸 🌹 🌻 🌼 🌲 🌴 🍀 🍁",
        // Food
        "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🍍 🥝 🍅 🥑 🍆 🥕 🌽 🍞 🧀 🍳 🥞 🍗 🍖 🍔 🍟 🍕 🌭 🌮 🍝 🍜 🍲 🍣 🍤 🍚 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 ☕ 🍵 🍺 🍻 🥂 🍷",
        // Activity
        "⚽ 🏀 🏈 ⚾ 🎾 🏐 🎱 🏓 🏸 🥊 🎯 🎳 🎮 🎲 🎵 🎶 🎤 🎧 🎸 🎹 🎬 🎨 🏆 🥇 🥈 🥉 🏅 🎉 🎊 🎁 🎈",
        // Travel
        "🚗 🚕 🚌 🚎 🏎️ 🚓 🚑 🚒 🚚 🚜 🏍️ 🚲 🚂 ✈️ 🚀 🚁 ⛵ 🚢 ⚓ 🏠 🏢 🏥 🏫 🕌 ⛪ 🌍 🌙 ☀️ ⛅ ☁️ 🌧️ ⚡ ❄️ 🌈 🌊",
        // Objects
        "📱 💻 ⌨️ 🖥️ 🖨️ 📷 📺 📻 ⏰ ⌛ 🔋 💡 🔦 📚 📖 📝 ✏️ 📎 📌 ✂️ 🔑 🔒 🔓 🔔 💰 💳 💎 🔧 🔨 🧲 💊 🛒 🎒 👓 👜 👑 👗 👟",
        // Symbols
        "✅ ❌ ❎ ⭕ ❗ ❓ ‼️ ⚠️ 🚫 ➕ ➖ ✖️ ➗ ➡️ ⬅️ ⬆️ ⬇️ 🔄 🔁 ▶️ ⏸️ ⏹️ 🔴 🟢 🔵 ⚫ ⚪ ©️ ®️ ™️ ♻️ 🔞 🆗 🆒 🆕 🆓 💲"
    };

    public static int count() { return DATA.length; }

    /** Emoji of category i (empty array for a bad index). */
    public static String[] list(int i) {
        if (i < 0 || i >= DATA.length) return new String[0];
        return DATA[i].split(" ");
    }
}
