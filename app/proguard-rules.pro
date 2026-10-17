# Имена enum сохраняются в настройках (жесты: Action.name / Press.name) — не трогаем
-keepclassmembers enum com.lsucu.pods.** { *; }
# drawable img_* берутся по имени через getIdentifier — не вырезать как «неиспользуемые»
