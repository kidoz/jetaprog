package su.kidoz.jetaprog.editor.syntax

import su.kidoz.jetaprog.editor.syntax.c.CLexer
import su.kidoz.jetaprog.editor.syntax.cmake.CMakeLexer
import su.kidoz.jetaprog.editor.syntax.cpp.CppLexer
import su.kidoz.jetaprog.editor.syntax.gitignore.GitignoreLexer
import su.kidoz.jetaprog.editor.syntax.go.GoLexer
import su.kidoz.jetaprog.editor.syntax.java.JavaLexer
import su.kidoz.jetaprog.editor.syntax.javascript.JavaScriptLexer
import su.kidoz.jetaprog.editor.syntax.javascript.TypeScriptLexer
import su.kidoz.jetaprog.editor.syntax.kotlin.KotlinLexer
import su.kidoz.jetaprog.editor.syntax.markdown.MarkdownLexer
import su.kidoz.jetaprog.editor.syntax.meson.MesonLexer
import su.kidoz.jetaprog.editor.syntax.python.PythonLexer
import su.kidoz.jetaprog.editor.syntax.rust.RustLexer
import su.kidoz.jetaprog.editor.syntax.toml.TomlLexer
import su.kidoz.jetaprog.editor.syntax.vala.ValaLexer
import su.kidoz.jetaprog.editor.syntax.xml.XmlLexer

/**
 * Registers every hand-written lexer bundled with the IDE into [LexerRegistry].
 */
public object BuiltinLexers {
    /**
     * Registers all built-in lexers. Safe to call more than once.
     */
    public fun registerAll() {
        LexerRegistry.register(KotlinLexer())
        LexerRegistry.register(ValaLexer())
        LexerRegistry.register(GoLexer())
        LexerRegistry.register(JavaLexer())
        LexerRegistry.register(JavaScriptLexer())
        LexerRegistry.register(TypeScriptLexer())
        LexerRegistry.register(RustLexer())
        LexerRegistry.register(CLexer())
        LexerRegistry.register(CppLexer())
        LexerRegistry.register(MesonLexer())
        LexerRegistry.register(CMakeLexer())
        LexerRegistry.register(XmlLexer())
        LexerRegistry.register(TomlLexer())
        LexerRegistry.register(MarkdownLexer())
        LexerRegistry.register(PythonLexer())
        LexerRegistry.register(GitignoreLexer())
    }
}
