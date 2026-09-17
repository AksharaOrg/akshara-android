import re
with open('app/src/main/java/org/slashboard/ime/ime/KeyboardView.kt', 'r') as f:
    text = f.read()

# I will find the last occurrence of "fun openTemplates" and keep everything BEFORE it up to the VERY FIRST "fun openNotes()"
# Actually, the file is 1014 lines now. Maybe `clean2.py` removed openTemplates?
# Let's just find out if we have bindTemplates
