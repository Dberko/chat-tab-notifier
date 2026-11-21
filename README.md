# Chat Tab Notifier



https://github.com/user-attachments/assets/79112911-7d77-43eb-ae03-cf1be00f45f6



Highlights the chat tab buttons along the bottom left of the chatbox when a channel has something
you have not read, so a private message or a clan ping does not slide past while you are reading
the Game tab.

```
 [ All ][ Game ][ Public ][ Private ][ Channel ][ Clan ][ Trade ]
                           ^^^^^^^^^            ^^^^^^
                           pulsing green        pulsing orange
```

## Using it

Nothing to set up. Out of the box the Private, Channel, Clan and Trade tabs pulse when a message
lands on them, and the highlight clears the moment you open that tab.

Game and Public are off by default. Both run constantly — every bone buried and everyone shouting in
Varrock — so turning them on mostly means a tab that is always lit.

## Configuration

**Channels** — one switch per tab: Game, Public, Private, Channel, Clan, Trade. The All tab is not
listed; it already shows every channel, so it can never be the one holding something unread.

**Appearance**

| | |
| --- | --- |
| Highlight | An outline around the button, a translucent fill over it, or a bar along the bottom edge |
| Pulse | Fade in and out, or hold steady |
| Pulse length | How long one fade takes, 200–4000ms |
| Thickness | Outline width, or the height of the underline bar |
| Corner rounding | Rounds the highlight to follow the tab button's own corners, 0–8px. Zero draws square corners |

Rounded corners are antialiased, since an unsmoothed curve on a button 16 pixels tall reads as a
staircase. The underline is clipped to the rounded outline, so its ends curve in with the button
rather than overhanging the corners. The 3px default is eyeballed against the vanilla button sprite
— nudge it if it sits proud of the real shape.

The highlight is drawn around the button's background sprite, not around the tab's container widget.
The container is a few pixels taller than the face it holds, which left the highlight hanging below
the button. `::chattab bounds` prints both rectangles if they ever need checking again.

**Colours** — a colour per tab, with alpha, so you can tell what arrived without reading it.
Defaults are green for Private, purple for Channel, orange for Clan, gold for Trade.

**Behaviour**

| | |
| --- | --- |
| Skip the open tab | Do not highlight the tab you already have open. On by default |
| All tab counts as read | Treat sitting on the All tab as having read everything. Off by default, because catching a message in that flood is the usual reason to want this plugin |
| Ignore channel notices | Skip joins, leaves and the other notices a clan or friends chat posts about itself, so only what somebody actually typed lights the tab. On by default. The Game tab is unaffected |
| Give up after | Drop a highlight on its own after this long. Zero keeps it until you open the tab |

**Notifications** — optionally a tray message, sound or window flash per channel, using RuneLite's
own notification settings. Off everywhere by default. Only the message that lights a dark tab
notifies, so a conversation in progress pings once rather than on every line.

**Sounds** — a sound per channel, plus one for messages you send yourself, in the spirit of an old
instant messenger. Off everywhere by default.

| | |
| --- | --- |
| Game, Public, Private, Channel, Clan, Trade | What a message arriving on that tab sounds like |
| Message you send | What a line you typed sounds like, in any channel |
| Custom sound id | Any of the game's own effect ids, for the Custom choice |
| Volume | Silent through to full |

The built-in choices are the game's own effects — boop, ding-a-ling, bell ding and dong, coin
tinkle, bloop, anvil tink, door open and close, woosh — played through the same engine as the rest
of the game.

### Your own sound files

Pick **Your own sound file** for a channel and it plays a file of yours instead. Drop the files in:

```
~/.runelite/chat-tab-notifier/
```

named after the tab, in lower case, plus `send` for messages you send:

```
game.wav  public.wav  private.wav  channel.wav  clan.wav  trade.wav  send.wav
```

The folder is created for you when the plugin starts. `::chattab sounds` prints its path and says
which files it found, so a silent channel can be told from a misnamed file.

**WAV, AIFF and AU only.** Those are what Java reads on its own, which keeps the plugin free of
extra dependencies. MP3 will not play — convert it first.

No sounds are shipped with the plugin, and none ever will be: the built-in choices are the game's
own effects, and anything else is a file of yours that stays on your machine.

For a messenger feel, pair a woosh on send with a boop or ding-a-ling on Private — or drop in your
own pair and point Private and send at them.

Unlike the notification above, these play on **every** qualifying message, not just the first one
that lights a dark tab.

The channel sounds follow the same rules as the highlight, so a channel you have switched off stays
silent, and so does a message arriving on the tab you already have open. The send sound does not: it
plays whenever you send something, in any channel, whether or not that channel is switched on, since
it is about what you did rather than about what you have left unread. A line you typed yourself
plays the send sound and never a channel sound as well, so one message can never make two noises.

## Testing it without waiting for real chat

Real chat is a bad test subject: the channels worth highlighting are the quiet ones, and the loud
ones are off by default. Three ways to force the issue, in rising order of effort.

**Switch on the Game channel.** It fires on nearly every action, so the Game tab lights within
seconds of doing anything. Needs no code and no restart — it is the fastest smoke test that the
plugin draws at all. Switch it back off afterwards.

**Talk to yourself.** Your own public and clan lines come back to your client as ordinary
player-typed messages, so typing `//test` in clan chat lights the Clan tab, as long as you are not
sitting on it.

**Turn on `::chattab`** under Debugging. Only your own client ever sees any of it; nothing is sent
anywhere.

| | |
| --- | --- |
| `::chattab` | Fakes a message in every enabled channel at once — good for comparing colours side by side |
| `::chattab clan` | One channel. Takes `game`, `public`, `private`, `channel`, `clan`, `trade`, and the nicknames `pm`, `fc`, `cc` |
| `::chattab draw` | Lights every enabled tab directly, without going through chat at all |
| `::chattab bounds` | Prints what each tab occupies on screen — the container widget and the button face inside it — for lining the highlight up against real numbers |
| `::chattab sounds` | Prints the sound folder and which of your own sound files are in it, with their sizes |

That last one is the useful one when something is broken, because it splits the problem in half: if
`::chattab draw` lights the tabs but `::chattab` does not, the drawing works and the fault is in
routing a message to a tab. If neither lights anything, it is the drawing.

**Turn on "Log decisions"** when you want to know *why*. Every chat message then writes a line to
`~/.runelite/logs/client.log` naming its tab and what happened to it:

```
PRIVATECHAT on the Private tab: lighting it up
CLAN_MESSAGE on the Clan tab: skipped, it is a channel notice and those are ignored
PUBLICCHAT on the Public tab: skipped, the Public channel is switched off
DIALOG: not filed under any tab
```

Without that, a deliberate skip and a broken plugin look identical. Follow the log live with
`Get-Content "$env:USERPROFILE\.runelite\logs\client.log" -Wait -Tail 0`. It is chatty — leave it
off the rest of the time.

## Behaviour worth knowing

- **Your own messages never count.** Outgoing private messages and your own trade offers are filed
  by the client under separate types, and those are ignored, so answering someone does not light
  their tab.
- **Dialogue is not chat.** NPC dialogue, mesboxes and overhead text do not belong to a tab and are
  skipped.
- **Nothing is written to the client.** The plugin reads which tab is open and draws over the
  buttons; it never clicks, types or changes a setting.
- **No network, no storage.** Nothing is uploaded and nothing is saved between sessions. Logging out
  or hopping clears every highlight. The only file it ever reads is a sound file you put in its own
  folder yourself, and it only reads one when you have asked for it.
- **Sound files are read off the client thread.** Opening a file and an audio line both block, so
  that work goes to RuneLite's executor and the game never waits on it.
- **While the chatbox is gone** — a cutscene, a fullscreen interface — there is nowhere to draw. The
  tab stays flagged and the highlight comes back with the chatbox.

## How it knows which tab is open

A message on the tab you are already reading needs no highlight, so the plugin has to know which tab
that is. The open tab's button wears a sprite of its own, which is read first because it describes
itself and survives a renumbering. If no tab reports it, the `CHAT_VIEW` client variable is read
instead, numbered left to right from zero.

Widget, sprite and variable ids live in [`Chatbox.java`](src/main/java/com/chattabnotifier/Chatbox.java)
rather than coming from `net.runelite.api.gameval`. Those classes hold nothing but compile time
constants, so they are stripped out of the runtime api jar the client ships and cannot be compiled
against without pulling the full jar from RuneLite's maven repository. javac inlines the numbers
either way, so this produces the same bytecode and keeps the plugin buildable against the jars the
client already downloaded.

## Building

```
.\build.ps1 -Verify   # compile and run the self check
.\build.ps1           # compile, package, install for sideloading
.\build.ps1 -Run      # compile and launch a client with the plugin loaded
.\build.ps1 -Clean    # delete build/ first
```

`build.ps1` compiles against the jars RuneLite already downloaded into
`~/.runelite/repository2`, so the only tool it needs is a JDK 11 or newer — it skips a `JAVA_HOME`
pointing at anything older. Gradle works too (`gradle build`) if you would rather pull the
dependencies from RuneLite's repository.

The self check covers the parts that hold no client state: which tab each message type lands on,
the `CHAT_VIEW` fallback, the pulse and fade maths, and that a message faked by `::chattab` really
does route back to the tab the command claims.

It also asserts that every type the config interface hands back is public. That one is not fussiness:
RuneLite implements the interface as a dynamic proxy in another package, so a package private return
type compiles cleanly and then throws `IllegalAccessError` from inside the client's draw loop the
first time a tab lights up — which freezes the client rather than disabling the plugin.

### Getting it to actually load

`~/.runelite/sideloaded-plugins` is only read in developer mode, and the official launcher (so the
Jagex Launcher too) forces developer mode off. Either start a client from here with
`.\build.ps1 -Run`, or start RuneLite yourself with `--developer-mode`.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
