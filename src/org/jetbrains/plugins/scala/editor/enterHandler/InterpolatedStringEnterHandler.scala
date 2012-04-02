package org.jetbrains.plugins.scala
package editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import lang.lexer.ScalaTokenTypes
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Ref
import com.intellij.lexer.StringLiteralLexer
import com.intellij.psi.{StringEscapesTokenTypes, PsiFile}

/**
 * User: Dmitry Naydanov
 * Date: 3/31/12
 */

class InterpolatedStringEnterHandler extends EnterHandlerDelegateAdapter {
  override def preprocessEnter(file: PsiFile, editor: Editor, caretOffset: Ref[Integer], caretAdvance: Ref[Integer],
                               dataContext: DataContext, originalHandler: EditorActionHandler): Result = {
    var offset = editor.getCaretModel.getOffset
    val element = file.findElementAt(offset)

    import ScalaTokenTypes._

    def modifyOffset(moveOn: Int) {
      offset += moveOn
      caretOffset.set(caretOffset.get + moveOn)
    }


    Option(element).foreach(a =>
      if (Set(tINTERPOLATED_STRING, tINTERPOLATED_STRING_ESCAPE, tINTERPOLATED_STRING_END).contains(a.getNode.getElementType)) {
        a.getParent.getFirstChild.getNode match {
          case b: ASTNode if b.getElementType == tINTERPOLATED_STRING_ID =>
            if (a.getNode.getElementType == tINTERPOLATED_STRING_ESCAPE) {
              if (caretOffset.get - a.getTextOffset == 1) {
                modifyOffset(1)
              }
            } else {
              val lexer: StringLiteralLexer = new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, a.getNode.getElementType)
              lexer.start(a.getText, 0, a.getTextLength)

              do {
                if (lexer.getTokenStart + a.getTextOffset < caretOffset.get && caretOffset.get() < lexer.getTokenEnd + a.getTextOffset) {
                  if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(lexer.getTokenType)) {
                    modifyOffset(lexer.getTokenEnd + a.getTextOffset - caretOffset.get())
                  }
                }
              } while (caretOffset.get() > lexer.getTokenEnd + a.getTextOffset && (lexer.advance(), lexer.getTokenType != null)._2)
            }


            extensions.inWriteAction {
              caretOffset.set(caretOffset.get + 3)
              caretAdvance.set(b.getTextLength + 1)
              editor.getDocument.insertString(offset, "\" +" + b.getText + "\"")
            }
            return Result.Continue
          case _ =>
        }
      })

    Result.Continue
  }
}
