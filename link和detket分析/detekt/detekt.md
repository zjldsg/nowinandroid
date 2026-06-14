# detekt

## Metrics

* 46 number of properties

* 20 number of functions

* 9 number of classes

* 5 number of packages

* 9 number of kt files

## Complexity Report

* 1,015 lines of code (loc)

* 667 source lines of code (sloc)

* 396 logical lines of code (lloc)

* 245 comment lines of code (cloc)

* 38 cyclomatic complexity (mcc)

* 27 cognitive complexity

* 6 number of total code smells

* 36% comment source ratio

* 95 mcc per 1,000 lloc

* 15 code smells per 1,000 lloc

## Findings (6)

### complexity, LongMethod (2)

One method should have one responsibility. Long methods tend to handle many things at once. Prefer smaller methods to make them easier to understand.

[Documentation](https://detekt.dev/docs/rules/complexity#longmethod)

* C:/Users/mac/Downloads/nowinandroid-main/nowinandroid-main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/MainActivity.kt:78:18
```
The function onCreate is too long (63). The maximum length is 60.
```
```kotlin
75 
76     private val viewModel: MainActivityViewModel by viewModels()
77 
78     override fun onCreate(savedInstanceState: Bundle?) {
!!                  ^ error
79         val splashScreen = installSplashScreen()
80         super.onCreate(savedInstanceState)
81 

```

* C:/Users/mac/Downloads/nowinandroid-main/nowinandroid-main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaApp.kt:141:14
```
The function NiaApp is too long (117). The maximum length is 60.
```
```kotlin
138     ExperimentalComposeUiApi::class,
139     ExperimentalMaterial3AdaptiveApi::class,
140 )
141 internal fun NiaApp(
!!!              ^ error
142     appState: NiaAppState,
143     showSettingsDialog: Boolean,
144     onSettingsDismissed: () -> Unit,

```

### style, ForbiddenComment (2)

Flags a forbidden comment.

[Documentation](https://detekt.dev/docs/rules/style#forbiddencomment)

* C:/Users/mac/Downloads/nowinandroid-main/nowinandroid-main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaApp.kt:124:21
```
Potential unfinished work
```
```kotlin
121                 NiaApp(
122                     appState = appState,
123 
124                     // TODO: Settings should be a dialog screen
!!!                     ^ error
125                     showSettingsDialog = showSettingsDialog,
126                     onSettingsDismissed = { showSettingsDialog = false },
127                     onTopAppBarActionClick = { showSettingsDialog = true },

```

* C:/Users/mac/Downloads/nowinandroid-main/nowinandroid-main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaApp.kt:274:17
```
Potential unfinished work
```
```kotlin
271                     )
272                 }
273 
274                 // TODO: We may want to add padding or spacer when the snackbar is shown so that
!!!                 ^ error
275                 //  content doesn't display behind it.
276             }
277         }

```

### style, MagicNumber (2)

Report magic numbers. Magic number is a numeric literal that is not defined as a constant and hence it's unclear what the purpose of this number is. It's better to declare such numbers as constants and give them a proper name. By default, -1, 0, 1, and 2 are not considered to be magic numbers.

[Documentation](https://detekt.dev/docs/rules/style#magicnumber)

* C:/Users/mac/Downloads/nowinandroid-main/nowinandroid-main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaApp.kt:293:36
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
290                 // however, its parameters are private, so we must depend on them implicitly
291                 // (NavigationBarTokens.ActiveIndicatorWidth = 64.dp)
292                 center = center + Offset(
293                     64.dp.toPx() * .45f,
!!!                                    ^ error
294                     32.dp.toPx() * -.45f - 6.dp.toPx(),
295                 ),
296             )

```

* C:/Users/mac/Downloads/nowinandroid-main/nowinandroid-main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaApp.kt:294:37
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
291                 // (NavigationBarTokens.ActiveIndicatorWidth = 64.dp)
292                 center = center + Offset(
293                     64.dp.toPx() * .45f,
294                     32.dp.toPx() * -.45f - 6.dp.toPx(),
!!!                                     ^ error
295                 ),
296             )
297         }

```

generated with [detekt version 1.23.7](https://detekt.dev/) on 2026-06-08 11:51:02 UTC
