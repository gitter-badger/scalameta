package scala.meta
package internal
package tokenizers

import scala.collection.mutable
import org.scalameta._
import org.scalameta.invariants._
import LegacyToken._
import scala.annotation.tailrec
import scala.meta.inputs._
import scala.meta.tokens._
import scala.meta.tokenizers._
import scala.meta.internal.tokenizers.PlatformTokenizerCache._

class ScalametaTokenizer(input: Input, dialect: Dialect) {
  def tokenize(): Tokens = {
    val miniCache = {
      var result = megaCache.get(dialect)
      if (result == null) {
        val unsyncResult = newUnsyncResult
        val syncResult = putIfAbsent(dialect, unsyncResult)
        result = if (syncResult != null) syncResult else unsyncResult
      }
      result
    }
    val entry = miniCacheSyncRoot.synchronized { miniCache.get(input) }
    entry match {
      case Some(tokens) =>
        tokens
      case None =>
        val tokens = uncachedTokenize()
        miniCacheSyncRoot.synchronized { miniCache.update(input, tokens) }
        tokens
    }
  }

  private def uncachedTokenize(): Tokens = {
    def legacyTokenToToken(curr: LegacyTokenData): Token = {
      (curr.token: @scala.annotation.switch) match {
        case IDENTIFIER       => Token.Ident(input, dialect, curr.offset, curr.endOffset + 1, curr.name)
        case BACKQUOTED_IDENT => Token.Ident(input, dialect, curr.offset, curr.endOffset + 1, curr.name)

        case INTLIT          => Token.Constant.Int(input, dialect, curr.offset, curr.endOffset + 1, curr.intVal)
        case LONGLIT         => Token.Constant.Long(input, dialect, curr.offset, curr.endOffset + 1, curr.longVal)
        case FLOATLIT        => Token.Constant.Float(input, dialect, curr.offset, curr.endOffset + 1, curr.floatVal)
        case DOUBLELIT       => Token.Constant.Double(input, dialect, curr.offset, curr.endOffset + 1, curr.doubleVal)
        case CHARLIT         => Token.Constant.Char(input, dialect, curr.offset, curr.endOffset + 1, curr.charVal)
        case SYMBOLLIT       => Token.Constant.Symbol(input, dialect, curr.offset, curr.endOffset + 1, scala.Symbol(curr.strVal))
        case STRINGLIT       => Token.Constant.String(input, dialect, curr.offset, curr.endOffset + 1, curr.strVal)
        case STRINGPART      => unreachable
        case TRUE            => Token.KwTrue(input, dialect, curr.offset)
        case FALSE           => Token.KwFalse(input, dialect, curr.offset)
        case NULL            => Token.KwNull(input, dialect, curr.offset)

        case INTERPOLATIONID => Token.Interpolation.Id(input, dialect, curr.offset, curr.endOffset + 1, curr.name)
        case XMLLIT          => Token.Xml.Start(input, dialect, curr.offset, curr.offset)
        case XMLLITEND       => unreachable

        case NEW   => Token.KwNew(input, dialect, curr.offset)
        case THIS  => Token.KwThis(input, dialect, curr.offset)
        case SUPER => Token.KwSuper(input, dialect, curr.offset)

        case IMPLICIT  => Token.KwImplicit(input, dialect, curr.offset)
        case OVERRIDE  => Token.KwOverride(input, dialect, curr.offset)
        case PROTECTED => Token.KwProtected(input, dialect, curr.offset)
        case PRIVATE   => Token.KwPrivate(input, dialect, curr.offset)
        case ABSTRACT  => Token.KwAbstract(input, dialect, curr.offset)
        case FINAL     => Token.KwFinal(input, dialect, curr.offset)
        case SEALED    => Token.KwSealed(input, dialect, curr.offset)
        case LAZY      => Token.KwLazy(input, dialect, curr.offset)
        case MACRO     => Token.KwMacro(input, dialect, curr.offset)

        case PACKAGE    => Token.KwPackage(input, dialect, curr.offset)
        case IMPORT     => Token.KwImport(input, dialect, curr.offset)
        case CLASS      => Token.KwClass(input, dialect, curr.offset)
        case CASECLASS  => unreachable
        case OBJECT     => Token.KwObject(input, dialect, curr.offset)
        case CASEOBJECT => unreachable
        case TRAIT      => Token.KwTrait(input, dialect, curr.offset)
        case EXTENDS    => Token.KwExtends(input, dialect, curr.offset)
        case WITH       => Token.KwWith(input, dialect, curr.offset)
        case TYPE       => Token.KwType(input, dialect, curr.offset)
        case FORSOME    => Token.KwForsome(input, dialect, curr.offset)
        case DEF        => Token.KwDef(input, dialect, curr.offset)
        case VAL        => Token.KwVal(input, dialect, curr.offset)
        case VAR        => Token.KwVar(input, dialect, curr.offset)
        case ENUM       => Token.KwEnum(input, dialect, curr.offset)

        case IF      => Token.KwIf(input, dialect, curr.offset)
        case THEN    => unreachable
        case ELSE    => Token.KwElse(input, dialect, curr.offset)
        case WHILE   => Token.KwWhile(input, dialect, curr.offset)
        case DO      => Token.KwDo(input, dialect, curr.offset)
        case FOR     => Token.KwFor(input, dialect, curr.offset)
        case YIELD   => Token.KwYield(input, dialect, curr.offset)
        case THROW   => Token.KwThrow(input, dialect, curr.offset)
        case TRY     => Token.KwTry(input, dialect, curr.offset)
        case CATCH   => Token.KwCatch(input, dialect, curr.offset)
        case FINALLY => Token.KwFinally(input, dialect, curr.offset)
        case CASE    => Token.KwCase(input, dialect, curr.offset)
        case RETURN  => Token.KwReturn(input, dialect, curr.offset)
        case MATCH   => Token.KwMatch(input, dialect, curr.offset)

        case LPAREN   => Token.LeftParen(input, dialect, curr.offset)
        case RPAREN   => Token.RightParen(input, dialect, curr.offset)
        case LBRACKET => Token.LeftBracket(input, dialect, curr.offset)
        case RBRACKET => Token.RightBracket(input, dialect, curr.offset)
        case LBRACE   => Token.LeftBrace(input, dialect, curr.offset)
        case RBRACE   => Token.RightBrace(input, dialect, curr.offset)

        case COMMA     => Token.Comma(input, dialect, curr.offset)
        case SEMI      => Token.Semicolon(input, dialect, curr.offset)
        case DOT       => Token.Dot(input, dialect, curr.offset)
        case COLON     => Token.Colon(input, dialect, curr.offset)
        case EQUALS    => Token.Equals(input, dialect, curr.offset)
        case AT        => Token.At(input, dialect, curr.offset)
        case HASH      => Token.Hash(input, dialect, curr.offset)
        case USCORE    => Token.Underscore(input, dialect, curr.offset)
        case ARROW     => Token.RightArrow(input, dialect, curr.offset, curr.endOffset + 1)
        case LARROW    => Token.LeftArrow(input, dialect, curr.offset, curr.endOffset + 1)
        case SUBTYPE   => Token.Subtype(input, dialect, curr.offset)
        case SUPERTYPE => Token.Supertype(input, dialect, curr.offset)
        case VIEWBOUND => Token.Viewbound(input, dialect, curr.offset)

        case WHITESPACE =>
          if (curr.strVal == " ") Token.Space(input, dialect, curr.offset)
          else if (curr.strVal == "\t") Token.Tab(input, dialect, curr.offset)
          else if (curr.strVal == "\r") Token.CR(input, dialect, curr.offset)
          else if (curr.strVal == "\n") Token.LF(input, dialect, curr.offset)
          else if (curr.strVal == "\f") Token.FF(input, dialect, curr.offset)
          else unreachable(debug(curr.strVal))

        case COMMENT   =>
          var value = new String(input.chars, curr.offset, curr.endOffset - curr.offset + 1)
          if (value.startsWith("//")) value = value.stripPrefix("//")
          if (value.startsWith("/*")) value = value.stripPrefix("/*").stripSuffix("*/")
          Token.Comment(input, dialect, curr.offset, curr.endOffset + 1, value)

        case ELLIPSIS  => Token.Ellipsis(input, dialect, curr.offset, curr.endOffset + 1, curr.base)
        case UNQUOTE   => Token.Unquote(input, dialect, curr.offset, curr.endOffset + 1)

        case EOF       => Token.EOF(input, dialect)

        case EMPTY    => unreachable
        case UNDEF    => unreachable
        case ERROR    => unreachable
      }
    }

    val legacyTokens: Array[LegacyTokenData] = {
      val scanner = new LegacyScanner(input, dialect)
      val legacyTokenBuf = new java.util.ArrayList[LegacyTokenData]()
      scanner.foreach(curr => legacyTokenBuf.add(new LegacyTokenData{}.copyFrom(curr)))
      val underlying = new Array[LegacyTokenData](legacyTokenBuf.size())
      legacyTokenBuf.toArray(underlying)
      underlying
    }
    val tokens = new java.util.ArrayList[Token]()
    tokens.add(Token.BOF(input, dialect))

    def loop(startingFrom: Int, braceBalance: Int = 0, returnWhenBraceBalanceHitsZero: Boolean = false): Int = {
      var legacyIndex = startingFrom
      def prev = legacyTokens(legacyIndex - 1)
      def curr = legacyTokens(legacyIndex)
      def emitToken() = tokens.add(legacyTokenToToken(curr))
      def nextToken() = legacyIndex += 1
      if (legacyIndex >= legacyTokens.length) return legacyIndex

      emitToken()
      nextToken()

      // NOTE: need to track this in order to correctly emit SpliceEnd tokens after splices end
      var braceBalance1 = braceBalance
      if (prev.token == LBRACE) braceBalance1 += 1
      if (prev.token == RBRACE) braceBalance1 -= 1
      if (braceBalance1 == 0 && returnWhenBraceBalanceHitsZero) return legacyIndex

      if (prev.token == INTERPOLATIONID) {
        // NOTE: funnily enough, messing with interpolation tokens is what I've been doing roughly 3 years ago, on New Year's Eve of 2011/2012
        // I vividly remember spending 2 or 3 days making scanner emit detailed tokens for string interpolations, and that was tedious.
        // Now we need to do the same for our new token stream, but I don't really feel like going through the pain again.
        // Therefore, I'm giving up the 1-to-1 legacy-to-new token correspondence and will be trying to reverse engineer sane tokens here rather than in scanner.
        var startEnd = prev.endOffset + 1
        while (startEnd < input.chars.length && input.chars(startEnd) == '\"') startEnd += 1
        val numStartQuotes = startEnd - prev.endOffset - 1
        val numQuotes = if (numStartQuotes <= 2) 1 else 3
        def emitStart(offset: Offset) = tokens.add(Token.Interpolation.Start(input, dialect, offset, offset + numQuotes))
        def emitEnd(offset: Offset) = tokens.add(Token.Interpolation.End(input, dialect, offset, offset + numQuotes))
        def emitContents(): Unit = {
          require(curr.token == STRINGPART || curr.token == STRINGLIT)
          if (curr.token == STRINGPART) {
            tokens.add(Token.Interpolation.Part(input, dialect, curr.offset, curr.endOffset + 1, curr.strVal))
            require(input.chars(curr.endOffset + 1) == '$')
            val dollarOffset = curr.endOffset + 1
            def emitSpliceStart(offset: Offset) = tokens.add(Token.Interpolation.SpliceStart(input, dialect, offset, offset + 1))
            def emitSpliceEnd(offset: Offset) = tokens.add(Token.Interpolation.SpliceEnd(input, dialect, offset, offset))
            def requireExpectedToken(expected: LegacyToken) = { require(curr.token == expected) }
            def emitExpectedToken(expected: LegacyToken) = { require(curr.token == expected); emitToken() }
            if (input.chars(dollarOffset + 1) == '{') {
              emitSpliceStart(dollarOffset)
              nextToken()
              legacyIndex = loop(legacyIndex, braceBalance = 0, returnWhenBraceBalanceHitsZero = true)
              emitSpliceEnd(curr.offset)
              emitContents()
            } else if (input.chars(dollarOffset + 1) == '_') {
              emitSpliceStart(dollarOffset)
              nextToken()
              emitExpectedToken(USCORE)
              nextToken()
              emitSpliceEnd(curr.offset)
              emitContents()
            } else {
              emitSpliceStart(dollarOffset)
              nextToken()
              require(curr.token == IDENTIFIER || curr.token == THIS)
              emitToken()
              nextToken()
              emitSpliceEnd(curr.offset)
              emitContents()
            }
          } else {
            curr.endOffset -= numQuotes
            tokens.add(Token.Interpolation.Part(input, dialect, curr.offset, curr.endOffset + 1, curr.strVal))
            require(input.chars(curr.endOffset + 1) == '\"')
            nextToken()
          }
        }
        // NOTE: before emitStart, curr is the first token that follows INTERPOLATIONID
        // i.e. STRINGLIT (if the interpolation is empty) or STRINGPART (if it's not)
        // NOTE: before emitEnd, curr is the first token that follows the concluding STRINGLIT of the interpolation
        // for example, EOF in the case of `q""` or `q"$foobar"`
        numStartQuotes match {
          case 1 => emitStart(curr.offset - 1); emitContents(); emitEnd(curr.offset - 1)
          case 2 => emitStart(curr.offset); curr.offset += 1; emitContents(); emitEnd(curr.offset - 1)
          case n if 3 <= n && n < 6 => emitStart(curr.offset - 3); emitContents(); emitEnd(curr.offset - 3)
          case 6 => emitStart(curr.offset - 3); emitContents(); emitEnd(curr.offset - 3)
        }
      }

      if (prev.token == XMLLIT) {
        def emitSpliceStart(offset: Offset) = tokens.add(Token.Xml.SpliceStart(input, dialect, offset, offset))
        def emitSpliceEnd(offset: Offset) = tokens.add(Token.Xml.SpliceEnd(input, dialect, offset, offset))
        def emitPart(from: Int, to: Int) = {
          tokens.add(Token.Xml.Part(input, dialect, from, to, new String(input.chars, from , to - from)))
        }

        @tailrec def emitContents(): Unit = {
          curr.token match {
            case XMLLIT =>
              emitPart(curr.offset, curr.endOffset + 1)
              nextToken()
              emitContents()

            case LBRACE =>
              // We are at the start of an embedded scala expression
              emitSpliceStart(curr.offset)
              legacyIndex = loop(legacyIndex, braceBalance = 0, returnWhenBraceBalanceHitsZero = true)
              emitSpliceEnd(curr.offset)
              emitContents()

            case XMLLITEND =>
              // We have reached the final xml part
              nextToken()
          }
        }

        // Xml.Start has been emitted. Backtrack to emit first part
        legacyIndex -= 1
        emitContents()
        assert(prev.token == XMLLITEND)
        val xmlEndIndex = prev.endOffset + 1
        tokens.add(Token.Xml.End(input, dialect, xmlEndIndex, xmlEndIndex))
      }

      loop(legacyIndex, braceBalance1, returnWhenBraceBalanceHitsZero)
    }

    loop(startingFrom = 0)
    val underlying = new Array[Token](tokens.size())
    tokens.toArray(underlying)
    Tokens(underlying, 0, underlying.length)
  }
}

object ScalametaTokenizer {
  def toTokenize: Tokenize = new Tokenize {
    def apply(input: Input, dialect: Dialect): Tokenized = {
      try {
        val tokenizer = new ScalametaTokenizer(input, dialect)
        Tokenized.Success(tokenizer.tokenize())
      } catch {
        case details @ TokenizeException(pos, message) => Tokenized.Error(pos, message, details)
      }
    }
  }
}
