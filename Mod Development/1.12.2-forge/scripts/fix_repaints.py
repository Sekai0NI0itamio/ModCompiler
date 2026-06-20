with open("src/main/java/com/bothelpers/client/gui/BotJobEditorFrame.java", "r") as f:
    text = f.read()

text = text.replace("                    repaint();", "                    repaint();\n                    BotJobEditorFrame.this.getGlassPane().repaint();")
text = text.replace("                repaint();", "                repaint();\n                BotJobEditorFrame.this.getGlassPane().repaint();")
text = text.replace("                        repaint();", "                        repaint();\n                        BotJobEditorFrame.this.getGlassPane().repaint();")

with open("src/main/java/com/bothelpers/client/gui/BotJobEditorFrame.java", "w") as f:
    f.write(text)
