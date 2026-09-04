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

The screen drains to grayscale, the wallpaper dims and notifications are held back — so when you pick the phone up at 1am out of habit, there is nothing there to hold you. Color comes back when the window ends.

WHY THIS ONE

Android already has a bedtime mode. On a lot of phones it silently never fires. It is scheduled as a background job, and every vendor's battery management gets a say in whether background jobs ever run. When something in that chain defers it there is no error and no notification — just a screen that stayed colorful all night.

Gloaming does not use a background job. It asks for the Alarms and reminders permission — the one you grant inside the app — which the system guarantees will fire at the minute you set. The app holds its own Do Not Disturb rule and switches it itself, so bedtime happens whether or not the app is open.

WHAT YOU GET

• A 24-hour dial. One window, dragged at either end. Days are chosen as the mornings you want the window to end on, so "the weekend" means Saturday and Sunday mornings.

• End bedtime at your alarm. Turn it on and the window follows whatever time your alarm is set for, instead of you keeping two schedules in step.

• A Quick Settings tile with three states rather than two: an hourglass while bedtime is only scheduled, a tick once it is actually running. A tick sitting there all evening while nothing is happening is the one thing a tick must not mean.

• An allowlist for the window — who can call, who can message, conversations, repeat callers, reminders, calendar events, media. The screen reports what the system says is in effect, not what the app believes it asked for.

• Screen effects, as far as your phone honors them: grayscale, wallpaper dimming, dark theme, always-on display. Where an effect does nothing on your device the switch is not drawn at all, rather than offering one that lies.

• It watches its own schedule. If the end of a window comes due and does not arrive on time, you are told, with a button to the setting that usually explains it.

• A boot watch, for phones that withhold the restart broadcast from apps their launch manager has decided are unimportant — which otherwise leaves a bedtime app armed with no alarms behind it.

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

Екран стає чорно-білим, шпалери притемнюються, а сповіщення притримуються — тож коли ви за звичкою берете телефон о першій ночі, там уже немає нічого, що б вас затримало. Колір повертається, коли проміжок завершується.

ЧОМУ САМЕ ЦЕЙ

В Android уже є режим сну. На багатьох телефонах він мовчки не спрацьовує. Він запланований як фонове завдання, а чи виконуються фонові завдання — вирішує керування батареєю кожного виробника. Якщо десь у цьому ланцюжку завдання відкладають, не буде ні помилки, ні сповіщення — лише екран, який усю ніч лишався кольоровим.

Gloaming не користується фоновим завданням. Він просить дозвіл «Будильники та нагадування» — той самий, який ви надаєте в застосунку, — і система гарантує спрацювання в задану хвилину. Застосунок тримає власне правило «Не турбувати» і перемикає його сам, тож режим сну настає незалежно від того, відкритий застосунок чи ні.

ЩО ВИ ОТРИМУЄТЕ

• 24-годинний циферблат. Одне вікно, яке тягнеться за обидва кінці. Дні обираються як ранки, якими вікно має закінчитися, тож «вихідні» означають ранок суботи й неділі.

• Завершення за будильником. Увімкніть — і вікно саме підлаштується під час вашого будильника, замість того щоб вести два розклади паралельно.

• Плитка у швидких налаштуваннях із трьома станами, а не двома: пісочний годинник, поки режим лише заплановано, і галочка, коли він справді діє. Галочка, що стоїть цілий вечір, поки нічого не відбувається, — це саме те, чого галочка означати не повинна.

• Список винятків на час вікна — хто може подзвонити, хто написати, розмови, повторні дзвінки, нагадування, події календаря, медіа. Екран показує те, що система вважає чинним, а не те, що застосунок гадає, ніби попросив.

• Ефекти екрана, наскільки їх підтримує ваш телефон: чорно-білий екран, притемнення шпалер, темна тема, always-on дисплей. Якщо ефект на вашому пристрої нічого не робить, перемикач не показується взагалі — краще жодного, ніж такий, що бреше.

• Він стежить за власним розкладом. Якщо кінець вікна настав, а сигнал не прийшов вчасно, вам про це скажуть — із кнопкою до налаштування, яке зазвичай це пояснює.

• Спостереження за перезавантаженням — для телефонів, які приховують від застосунків сигнал про перезапуск і залишають режим сну без жодного будильника за ним.

• Англійська, російська та українська, і формат часу — 12- або 24-годинний, як налаштовано на вашому телефоні.

ПРИВАТНІСТЬ

У Gloaming немає дозволу на інтернет. Не «не надсилає дані» — а не може. Немає ні аналітики, ні реклами, ні акаунтів, ні стороннього коду. Ваш розклад і власний журнал застосунку лишаються на телефоні, а видалення застосунку прибирає їх.

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

Успокойте свой телефон на нужный промежуток времени — настройте «Не беспокоить» и экранные эффекты в одном удобном интерфейсе. Всё работает, даже если приложение закрыто: оно пользуется разрешением «Будильники и напоминания», которое вы выдаёте в приложении. Это даёт гарантию срабатывания точно в заданное время и не проходит через планировщик фоновых задач.

Экран становится чёрно-белым, обои приглушаются, а уведомления придерживаются — и когда вы по привычке берёте телефон в час ночи, там уже нет ничего, что бы вас задержало. Цвет возвращается, когда промежуток заканчивается.

ПОЧЕМУ ИМЕННО ОН

В Android уже есть режим сна. На многих телефонах он молча не срабатывает. Он запланирован как фоновая задача, а выполняются ли фоновые задачи — решает управление батареей каждого производителя. Если где-то в этой цепочке задачу откладывают, не будет ни ошибки, ни уведомления — только экран, который всю ночь оставался цветным.

Gloaming не пользуется фоновой задачей. Он запрашивает разрешение «Будильники и напоминания» — то самое, которое вы выдаёте в приложении, — и система гарантирует срабатывание в заданную минуту. Приложение держит собственное правило «Не беспокоить» и переключает его само, так что режим сна наступает независимо от того, открыто приложение или нет.

ЧТО ВЫ ПОЛУЧАЕТЕ

• 24-часовой циферблат. Одно окно, которое тянется за оба конца. Дни выбираются как утра, которыми окно должно закончиться, так что «выходные» — это утро субботы и воскресенья.

• Завершение по будильнику. Включите — и окно само подстроится под время вашего будильника, вместо того чтобы вести два расписания параллельно.

• Плитка в быстрых настройках с тремя состояниями, а не двумя: песочные часы, пока режим только запланирован, и галочка, когда он действительно работает. Галочка, стоящая весь вечер, пока ничего не происходит, — это ровно то, чего галочка означать не должна.

• Список исключений на заданное время — кто может позвонить, кто написать, разговоры, повторные звонки, напоминания, события календаря, медиа. Экран показывает то, что система считает действующим, а не то, что приложение думает, будто запросило.

• Эффекты экрана, насколько их поддерживает ваш телефон: чёрно-белый экран, приглушение обоев, тёмная тема, always-on дисплей. Если эффект на вашем устройстве ничего не делает, переключатель не показывается вовсе — лучше никакого, чем такой, который врёт.

• Оно следит за собственным расписанием. Если заданное время подошло к концу, а сигнал не пришёл вовремя, вам об этом скажут — с кнопкой к настройке, которая обычно это объясняет.

• Наблюдение за перезагрузкой — для телефонов, которые скрывают от приложений сигнал о перезапуске и оставляют режим сна без единого будильника за ним.

• Английский, русский и украинский, и формат времени — 12- или 24-часовой, как настроено на вашем телефоне.

ПРИВАТНОСТЬ

У Gloaming нет разрешения на интернет. Не «не отправляет данные» — а не может. Нет ни аналитики, ни рекламы, ни аккаунтов, ни стороннего кода. Ваше расписание и собственный журнал приложения остаются на телефоне, а удаление приложения убирает их.

ТРЕБОВАНИЯ

Android 15 или новее. Два разрешения, оба выдаются из самого приложения: доступ к политике уведомлений (для «Не беспокоить») и «Будильники и напоминания».

ОТКРЫТЫЙ КОД

Лицензия Apache 2.0. Всё приложение открыто: github.com/jemcik/Gloaming
```

---

## Still to decide

- **Contact email.** The policy page points at GitHub issues. Play requires a
  contact email on the listing itself regardless; decide which address before
  filling the Console form, since it is displayed publicly.
