# Play store listing

Paste-ready copy for the Play Console listing. Kept here rather than only in the
Console so it is reviewable, diffable, and translatable alongside the app's own
strings.

Assets live in `docs/play/`, built by `python3 tools/play_assets.py`.

Character limits are Play's: **title 30**, **short description 80**, **full
description 4000**. `tools/play_assets.py` does not check them; the counts below
were measured and are re-checked by eye when the copy changes.

---

## Title (30 max)

```
Gloaming
```

## Short description (80 max)

```
Bedtime that keeps its own schedule: Do Not Disturb, grayscale, dimmed screen.
```

## Full description (4000 max)

```
Gloaming makes your phone boring — for exactly as long as you want it to be.

Quiet your phone for whatever stretch of time you want — set up Do Not Disturb and the screen effects in one place. It all works even with the app closed, because it uses the Alarms and reminders permission you grant in the app. That guarantees it fires at exactly the time you set, and it never goes through the background job scheduler.

WHY THIS ONE

Android already has a bedtime mode. On a lot of phones it does not fire until you open the app that runs it — or it fires, but late. That mode is scheduled as a background job, and every vendor's battery management gets a say in whether background jobs ever run. When something in that chain defers it there is no error and no notification — Do Not Disturb simply never comes on and the effects never apply.

Gloaming does not use a background job. It asks for the Alarms and reminders permission — the one you grant inside the app — which the system guarantees will fire at the minute you set. The app holds its own Do Not Disturb rule and switches it itself, so bedtime happens whether or not the app is open.

WHAT YOU GET

• A 24-hour dial. One window, set by dragging either end around the dial or by tapping a time and picking it exactly. You choose the mornings you want to wake up on, not the evenings you go to bed: pick Saturday and the window runs from Friday evening to Saturday morning.

• End bedtime at your alarm. Turn it on and the window follows whatever time your alarm is set for, instead of you keeping two schedules in step.

• A Quick Settings tile with three states rather than two. Three faces, one meaning each. Hourglass — bedtime is on and waiting for its hour. Tick — a window is running right now. Gloaming’s own mark — bedtime is off. The tile never shows a tick for something that has not started.

• An allowlist for the window — who can call, who can message, conversations, repeat callers, reminders, calendar events, media. The screen reports the system's current state, not the one the app switched on and assumes is in effect.

• Screen effects: grayscale, wallpaper dimming, dark theme, always-on display. Where the operating system does not let an app control an effect, the switch is not drawn at all, rather than offering one that lies. On Samsung phones, grayscale, the dark theme and wallpaper dimming run through your own Modes and Routines: Gloaming hands Samsung’s app each routine ready-made, you tap Save once, and from then on they are ordinary switches.

• It checks rather than assumes. Some phones freeze apps in the background, and the failure that causes is specific: the end of a window never fires, so bedtime does not end — whatever it switched on stays on into your day. Gloaming watches every end and says when one is late, with a button to the background setting that is usually the cause. It also asks once, eleven minutes after install, whether this phone delivers background alarms at all — silent unless the answer is no.

• A boot watch, for phones that never tell apps the phone restarted: bedtime stays switched on while nothing behind it will fire. Gloaming notices.

• English, Russian and Ukrainian, and clock times in whichever 12- or 24-hour format your phone is set to.

PRIVACY

Gloaming has no internet permission. Not "does not send data" — it cannot. There is no analytics, no advertising, no account and no third-party code. Your schedule and the app's own log stay on the phone, and uninstalling removes them.

REQUIREMENTS

Android 15 or newer. Two permissions, both granted from inside the app: notification policy access (for Do Not Disturb) and Alarms and reminders.

OPEN SOURCE

Apache License 2.0. The whole app is public at github.com/jemcik/Gloaming
```

---

# Українська (uk-UA)

The title is not translated. `app_name` is `translatable="false"` in
`strings.xml` and the wordmark is drawn into the feature graphic; a store that
called the app something else would be naming a different app.

## Short description (80 max)

```
Сон за розкладом: «Не турбувати», чорно-білий екран і притемнені шпалери.
```

## Full description (4000 max)

```
Gloaming робить ваш телефон нудним — рівно на той час, який ви оберете.

Заспокойте свій телефон на бажаний проміжок часу — налаштуйте «Не турбувати» і екранні ефекти в одному зручному інтерфейсі. Усе працює, навіть якщо застосунок закритий: він користується дозволом «Будильники та нагадування», який ви надаєте в застосунку. Це дає гарантію спрацювання точно в заданий час і не проходить через планувальник фонових завдань.

ЧОМУ САМЕ ЦЕЙ

В Android уже є режим сну. На багатьох телефонах він не спрацьовує, доки ви не відкриєте застосунок, який ним керує, — або спрацьовує, але невчасно. Режим сну запланований як фонове завдання, а чи виконуються фонові завдання — вирішує керування батареєю кожного виробника. Якщо десь у цьому ланцюжку завдання відкладають, не буде ні помилки, ні сповіщення — «Не турбувати» просто не вмикається, а ефекти не застосовуються.

Gloaming не користується фоновим завданням. Він просить дозвіл «Будильники та нагадування» — той самий, який ви надаєте в застосунку, — і система гарантує спрацювання в задану хвилину. Застосунок має власне правило «Не турбувати» і перемикає його сам, тож режим сну настає незалежно від того, відкритий застосунок чи ні.

ЩО ВИ ОТРИМУЄТЕ

• 24-годинний циферблат. Один проміжок: тягніть будь-який кінець по колу або торкніться часу й виберіть його точно. Ви обираєте не вечір, коли лягаєте, а ранок, коли хочете прокинутися: виберіть суботу — і проміжок триватиме з вечора п’ятниці до ранку суботи.

• Завершення за будильником. Увімкніть — і проміжок сам підлаштується під час вашого будильника, замість того щоб вести два розклади паралельно.

• Плитка у швидких налаштуваннях із трьома станами, а не двома. Три вигляди, кожен з одним значенням. Пісочний годинник — режим сну увімкнено, він чекає своєї години. Галочка — проміжок триває просто зараз. Власний знак Gloaming — режим сну вимкнено. Плитка ніколи не показує галочку для того, що ще не почалося.

• Список винятків на час проміжку — хто може подзвонити, хто написати, розмови, повторні дзвінки, нагадування, події календаря, медіа. Екран показує поточний стан системи, а не той, який застосунок увімкнув і вважає чинним.

• Ефекти екрана: чорно-білий екран, притемнення шпалер, темна тема, always-on дисплей. Якщо операційна система не дозволяє застосунку керувати ефектом, перемикач не показується взагалі — краще жодного, ніж такий, що бреше. На телефонах Samsung чорно-білий екран, темна тема й притемнення шпалер працюють через ваші власні «Режими й сценарії»: Gloaming передає програмі Samsung готовий сценарій, ви один раз натискаєте «Зберегти», і далі це звичайні перемикачі.

• Він перевіряє, а не припускає. Деякі телефони заморожують застосунки у фоні, і поломка при цьому конкретна: кінець проміжку не спрацьовує, тож режим сну не завершується — усе, що він увімкнув, лишається увімкненим уже вдень. Gloaming стежить за кожним кінцем і повідомляє, коли той запізнився, — із кнопкою до налаштування фонової роботи, яке зазвичай і є причиною. А ще одноразово, через одинадцять хвилин після встановлення, питає, чи доставляє цей телефон фонові сигнали взагалі, — і мовчить, якщо все гаразд.

• Спостереження за перезавантаженням — для телефонів, які не кажуть застосункам, що телефон перезавантажився: режим сну лишається увімкненим, але вже нічого не спрацює. Gloaming це помічає.

• Англійська, російська та українська, і формат часу — 12- або 24-годинний, як налаштовано на вашому телефоні.

ПРИВАТНІСТЬ

У Gloaming немає дозволу на інтернет. Це не обіцянка не надсилати дані, а обмеження, яке забезпечує операційна система. Немає ні аналітики, ні реклами, ні акаунтів, ні стороннього коду. Ваш розклад і власний журнал застосунку лишаються на телефоні, а видалення застосунку прибирає їх.

ВИМОГИ

Android 15 або новіший. Два дозволи, обидва надаються з самого застосунку: доступ до політики сповіщень (для «Не турбувати») і «Будильники та нагадування».

ВІДКРИТИЙ КОД

Ліцензія Apache 2.0. Увесь застосунок відкритий: github.com/jemcik/Gloaming
```

---

# Русский (ru-RU)

## Short description (80 max)

```
Сон по расписанию: «Не беспокоить», чёрно-белый экран и приглушённые обои.
```

## Full description (4000 max)

```
Gloaming делает телефон скучным — ровно на то время, которое вы выберете.

Успокойте свой телефон на нужный промежуток времени — настройте «Не беспокоить» и экранные эффекты в одном месте. Всё работает, даже если приложение закрыто: оно пользуется разрешением «Будильники и напоминания», которое вы выдаёте в приложении. Оно гарантированно срабатывает в заданное время и не проходит через планировщик фоновых задач.

ПОЧЕМУ ИМЕННО ОН

В Android уже есть режим сна. На многих телефонах он не срабатывает, пока вы не откроете приложение, которое им управляет, — или срабатывает, но не вовремя. Режим сна запланирован как фоновая задача, а выполняются ли фоновые задачи — решает управление батареей каждого производителя. Если где-то в этой цепочке задачу откладывают, не будет ни ошибки, ни уведомления — «Не беспокоить» просто не включается, а эффекты не применяются.

Gloaming не пользуется фоновой задачей. Он запрашивает разрешение «Будильники и напоминания» — то самое, которое вы выдаёте в приложении, — и система гарантирует срабатывание в заданную минуту. У Gloaming собственное правило «Не беспокоить», и он переключает его сам, так что режим сна наступает независимо от того, открыто приложение или нет.

ЧТО ВЫ ПОЛУЧАЕТЕ

• 24-часовой циферблат. Один промежуток: тяните любой конец по кругу или коснитесь времени и выберите его точно. Вы выбираете не вечер, когда ложитесь, а утро, когда хотите проснуться: выберите субботу — и промежуток продлится с вечера пятницы до утра субботы.

• Завершение по будильнику. Включите — и промежуток сам подстроится под время вашего будильника, вместо того чтобы вести два расписания параллельно.

• Плитка в быстрых настройках с тремя состояниями, а не двумя. Три вида, у каждого одно значение. Песочные часы — режим сна включён, он ждёт своего часа. Галочка — промежуток идёт прямо сейчас. Собственный знак Gloaming — режим сна выключен. Плитка никогда не показывает галочку для того, что ещё не началось.

• Список исключений на заданное время — кто может позвонить, кто написать, разговоры, повторные звонки, напоминания, события календаря, медиа. Экран показывает текущее состояние системы, а не то, которое приложение включило и считает рабочим.

• Эффекты экрана: чёрно-белый экран, приглушение обоев, тёмная тема, always-on дисплей. Если операционная система не позволяет приложению управлять эффектом, переключатель не показывается вовсе — лучше никакого, чем такой, который врёт. На Samsung чёрно-белый экран, тёмная тема и приглушение обоев идут через ваши «Режимы и сценарии»: Gloaming передаёт готовый сценарий, вы один раз нажимаете «Сохранить» — дальше это обычный переключатель.

• Он проверяет, а не предполагает. Некоторые телефоны замораживают приложения в фоне, и поломка конкретная: конец промежутка не срабатывает, поэтому режим сна не заканчивается — всё, что он включил, остаётся включённым уже днём. Gloaming следит за каждым концом и сообщает, когда тот опоздал, — с кнопкой к настройке фоновой работы, которая обычно и есть причина. А ещё однократно, через одиннадцать минут после установки, спрашивает, доставляет ли телефон фоновые сигналы вообще — и молчит, если всё в порядке.

• Наблюдение за перезагрузкой — для телефонов, которые не сообщают приложениям о перезагрузке: режим сна остаётся включённым, но уже ничего не сработает. Gloaming это замечает.

• Английский, русский и украинский, и формат времени — 12- или 24-часовой, как настроено на вашем телефоне.

ПРИВАТНОСТЬ

У Gloaming нет разрешения на интернет. Это не обещание не отправлять данные, а ограничение, которое обеспечивает система. Нет ни аналитики, ни рекламы, ни аккаунтов, ни стороннего кода. Ваше расписание и собственный журнал приложения остаются на телефоне, а удаление приложения убирает их.

ТРЕБОВАНИЯ

Android 15 или новее. Два разрешения, оба выдаются в приложении: доступ к политике уведомлений (для «Не беспокоить») и «Будильники и напоминания».

ОТКРЫТЫЙ КОД

Лицензия Apache 2.0. Всё приложение открыто: github.com/jemcik/Gloaming
```

---

# What’s new — 0.15 (500 max each)

en-US

```
End bedtime at your alarm now works properly. With the switch on, bedtime ends at whichever alarm rings first (Android shows apps only that one), and the row shows its time. The wake time you set in Gloaming applies on mornings with no alarm (say, alarms on weekdays only). With no alarms the switch is off. The button below opens your clock app with your alarms. Change the wake time by hand and "end bedtime at your alarm" switches off: the manual setting takes priority.
```

uk-UA

```
Завершення за будильником тепер працює як слід. З увімкненим перемикачем режим сну закінчується за будильником, який спрацює найпершим (Android показує лише його), а рядок показує, о котрій. Час пробудження, заданий у Gloaming, діє для ранків без будильника (скажімо, будильник лише на будні). Без будильників перемикач вимкнений. Кнопка нижче відкриває годинник із вашими будильниками. Змінили час пробудження вручну — режим за будильником вимикається: ручне налаштування важливіше.
```

ru-RU

```
Завершение по будильнику теперь работает как надо. С включённым переключателем режим сна заканчивается по будильнику, что сработает первым (Android показывает лишь его), а строка показывает, во сколько. Время пробуждения, заданное в Gloaming, действует для утр без будильника (скажем, будильник только по будням). Без будильников переключатель выключен. Кнопка ниже открывает часы с вашими будильниками. Изменили время пробуждения вручную — режим по будильнику выключается: ручная настройка важнее.
```

# What’s new — 0.14 (500 max each)

en-US

```
Samsung phones: grayscale, dark theme and wallpaper dimming now work. Each runs through your own Modes and Routines — Gloaming hands Samsung’s app the routine ready-made, you tap Save once, and from then on it is an ordinary switch. The app explains that one step before it, confirms when it is done, and says where the routines live so you can remove them. The Quick Settings tile keeps its own icon in the tile picker.
```

uk-UA

```
Телефони Samsung: чорно-білий екран, темна тема й притемнення шпалер тепер працюють. Кожен — через ваші «Режими й сценарії»: Gloaming передає програмі Samsung готовий сценарій, ви один раз натискаєте «Зберегти», і далі це звичайний перемикач. Застосунок пояснює цей крок наперед, підтверджує, коли все готово, і показує, де лежать сценарії, щоб їх можна було видалити. Плитка швидких налаштувань зберігає свій значок у списку плиток.
```

ru-RU

```
Телефоны Samsung: чёрно-белый экран, тёмная тема и приглушение обоев теперь работают. Каждый — через ваши «Режимы и сценарии»: Gloaming передаёт приложению Samsung готовый сценарий, вы один раз нажимаете «Сохранить», и дальше это обычный переключатель. Приложение объясняет этот шаг заранее, подтверждает, когда всё готово, и показывает, где лежат сценарии, чтобы их можно было удалить. Плитка быстрых настроек сохраняет свой значок в списке плиток.
```

---

## Contact details

Play shows these publicly on the store page.

- **Email:** `steroidev@gmail.com` — required, and a separate address from the
  one on the git commits, which is the point of choosing it deliberately.
- **Website:** `https://jemcik.github.io/Gloaming/`
- **Privacy policy:** `https://jemcik.github.io/Gloaming/privacy-policy.html` —
  set under App content, not here, and it is the same single URL for every
  language: the page carries its own switcher to the uk/ and ru/ copies.

The policy page also points at GitHub issues, which is where anything that
needs a thread should go; the email exists because Play requires one.
