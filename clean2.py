import re

with open('app/src/main/java/org/slashboard/ime/ime/KeyboardView.kt', 'r') as f:
    text = f.read()

# I will find the first occurrence of "fun openTemplates" and keep everything BEFORE it.
# Then I find the first occurrence of "fun openTemplates" and keep everything AFTER it.
# Wait, I just want to delete all instances of bindNotes and openNotes.
# Let's split by "    private fun bindNotes() {" and "body.addView(notesLayout)\n    }"

def remove_blocks():
    global text
    while True:
        start = text.find('    private fun bindNotes() {')
        if start == -1:
            break
        end = text.find('body.addView(notesLayout)\n    }', start)
        if end == -1:
            break
        text = text[:start] + text[end + len('body.addView(notesLayout)\n    }'):]

remove_blocks()

# Also remove stray layer = KeyboardLayer.NOTES and render()
text = re.sub(r'\s*layer = KeyboardLayer\.NOTES\n\s*render\(\)\n\s*\}\n', '', text)
text = re.sub(r'\s*fun openNotes\(\) \{\n', '', text)

with open('app/src/main/java/org/slashboard/ime/ime/KeyboardView.kt', 'w') as f:
    f.write(text)
