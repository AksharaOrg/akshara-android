import re

with open('app/src/main/java/org/slashboard/ime/ime/KeyboardView.kt', 'r') as f:
    text = f.read()

# The script repeated the block exactly as in patch_notes.sh. 
# We deleted "fun openNotes() {" with sed. So what remains are the rest of the lines.
# It's better to just regex remove the whole block.
# Let's find the string that was inserted.
# It started with:
#     layer = KeyboardLayer.NOTES
#     render()
# }
# 
# private fun bindNotes() {
# (and ended with)
#     body.addView(notesLayout)
# }

pattern = re.compile(r'^\s*layer = KeyboardLayer\.NOTES\n\s*render\(\)\n\s*\}\n\n\s*private fun bindNotes\(\) \{.*?\n\s*body\.addView\(notesLayout\)\n\s*\}\n', re.MULTILINE | re.DOTALL)
text = pattern.sub('', text)

with open('app/src/main/java/org/slashboard/ime/ime/KeyboardView.kt', 'w') as f:
    f.write(text)
