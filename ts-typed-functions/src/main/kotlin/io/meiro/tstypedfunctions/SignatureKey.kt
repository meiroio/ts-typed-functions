package io.meiro.tstypedfunctions

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSParameterListElement
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunctionType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeAlias
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor

object SignatureKey {

    fun of(alias: TypeScriptTypeAlias): String? {
        if (alias.typeParameters.isNotEmpty()) return null
        val fnType = alias.typeDeclaration as? TypeScriptFunctionType ?: return null
        return build(fnType.parameters.toList(), fnType.returnTypeElement)
    }

    fun of(fn: JSFunction): String? {
        if (fn is TypeScriptFunction && fn.typeParameters.isNotEmpty()) return null
        return build(fn.parameters.toList(), fn.returnTypeElement)
    }

    private fun build(parameters: List<JSParameterListElement>, returnType: PsiElement?): String? {
        val ret = returnType ?: return null
        val params = parameters.map { p ->
            val type = p.typeElement ?: return null
            buildString {
                if (p.isOptional) append("?")
                if (p.isRest) append("...")
                append(":")
                append(normalize(type))
            }
        }.joinToString(",")
        return "($params)=>${normalize(ret)}"
    }

    private fun normalize(element: PsiElement): String =
        stripComments(element).replace(Regex("\\s+"), "")

    private fun stripComments(element: PsiElement): String {
        val sb = StringBuilder()
        element.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(e: PsiElement) {
                if (e is PsiComment) return
                if (e.firstChild == null) sb.append(e.text)
                super.visitElement(e)
            }
        })
        return sb.toString()
    }
}
