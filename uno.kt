sealed interface UnoCard {
  sealed interface Colored : UnoCard {
    val color: UnoColor 
  }
  sealed interface Wildcard : UnoCard
  
  
  data class Number(
    val n: Int, 
    override val color: UnoColor
  ): Colored {
    override fun toString() = "$n ${color.name}"
  }
  data class Draw2(
    override val color: UnoColor
  ): Colored {
    override fun toString() = "Draw2 ${color.name}"
  }
  data class Skip(
    override val color: UnoColor
  ): Colored {
    override fun toString() = "Skip ${color.name}" 
  }
  data class Reverse(
    override val color: UnoColor
  ): Colored {
    override fun toString() = "Reverse ${color.name}"
  }
  data object ChangeColor : Wildcard {
     override fun toString() = "ChangeColor"
  }
      
  data object Draw4 : Wildcard {
    override fun toString() = "Draw4"
  }
}

enum class UnoColor {
  Red,
  Green,
  Blue,
  Yellow,
}

class UnoDeck(empty: Boolean = false) {
  private var cards: List<UnoCard> = buildList {
    if (empty) return@buildList
      
    repeat(2) { times ->
      UnoColor.entries.forEach { color ->
        for(n in 0..9) {
          if(n == 0 && times > 0) continue // only 1 
          add(UnoCard.Number(n, color))
        }
        add(UnoCard.Draw2(color))
        add(UnoCard.Skip(color))
        add(UnoCard.Reverse(color))
      }
    }
    repeat(4) {
      add(UnoCard.ChangeColor)
      add(UnoCard.Draw4)
    }
  }.shuffled()
  
  val size: Int
    get() = cards.size
  
  fun shuffle() {
    cards = cards.shuffled()
  }
  
  fun isEmpty() = cards.isEmpty()
  
  fun draw(): UnoCard {
    return peek().also {
      cards = cards.dropLast(1)
    }
  }
  
  fun push(card: UnoCard) {
    cards = cards + card    
  }
  
  fun pushUnder(card: UnoCard) {
    cards = listOf(card) + cards
  }
  
  fun peek(): UnoCard {
     return cards.last() 
  }
  
  fun clone(): UnoDeck {
    val cards = cards
    return UnoDeck(empty = true).also { cloned ->
      cards.forEach { cloned.push(it) }
    }
  }
  
  override fun toString() = "${cards.size} cards: ${cards.reversed().toString()}"    
}

interface Strategy {
  fun turn(
    hand: List<UnoCard>,
    lastCard: UnoCard,
    rotation: Rotation,
    color: UnoColor,
    mustDraw: MustDraw?,
  ): Turn
}

class Player(
  val name: String,
  private val strategy: Strategy,
) {
   private val _hand = mutableListOf<UnoCard>()
   val hand: List<UnoCard> = _hand
    
   // History
   private val _history = mutableListOf<Pair<List<UnoCard>, Turn>>()
   val history: List<Pair<List<UnoCard>, Turn>> = _history
    
   fun turn(
     lastCard: UnoCard,
     rotation: Rotation,
     color: UnoColor,
     mustDraw: MustDraw?,
   ): Turn {
     require(hand.isNotEmpty()) {
       "Cannot play with an empty hand!"
     }
     val turn = strategy.turn(
         hand = hand,
         lastCard = lastCard,
         rotation = rotation,
         color = color,
         mustDraw = mustDraw,
     )
     when(turn) {
       is Turn.Play -> {
         val card = turn.card
         if (mustDraw != null) {
           require(mustDraw.factor != DrawFactor.Four) {
             "Draw 4 can be answered only by draw 4"
           }
           require(card is UnoCard.Draw2) {
             "Draw 2 can be answered only by Draw 2 or Draw 4"
           }
         }
         require(valid(card, lastCard, color)) {
           "Can't play '$card' when '$lastCard' and $color"
         }
       }
       is Turn.PlayWildcard -> {
         if (mustDraw != null) {
           require(turn.card is UnoCard.Draw4) {
             "Draw 4 card required"
           }
         }
       }
       is Turn.Draw -> {
         require(
           hand.none { 
             it is UnoCard.Colored && it.color == color
           } || mustDraw != null
         ) {
           "Can't draw when you can match the color!"
         }
       }
     }
     _history.add(hand.toList() to turn)
     return turn
   }
   
   private fun valid(
       card: UnoCard.Colored,
       lastCard: UnoCard,
       color: UnoColor,
   ): Boolean {
     // Colors match
     if (card.color == color) return true
     // Numbers match
     if (card is UnoCard.Number &&
          lastCard is UnoCard.Number &&
          card.n == lastCard.n
        ) return true
     // Special same type
     if (card is UnoCard.Reverse && 
          lastCard is UnoCard.Reverse) return true
     if (card is UnoCard.Skip && 
          lastCard is UnoCard.Skip) return true
     if (card is UnoCard.Draw2 && 
          lastCard is UnoCard.Draw2) return true
       
     return false
   }
    
   fun play(card: UnoCard) {
     require(card in hand) {
       "Player $name doesn't have '$card' in hand: $hand"
     }
     _hand.remove(card)
   }
   
   fun draw(card: UnoCard) {
     _hand.add(card) 
   }
}

sealed interface Turn {
  data class Play(val card: UnoCard.Colored): Turn
  data class PlayWildcard(
      val card: UnoCard.Wildcard,
      val newColor: UnoColor,
  ): Turn
  data object Draw : Turn
}

enum class Rotation {
  Clockwise,
  CounterClockwise
}

enum class DrawFactor {
  Two, Four
}

data class MustDraw(
  val factor: DrawFactor,
  val cards: Int
)
   
class UnoGame(
  private val players: List<Player>,
  private val debug: Boolean = false,  
) {
  private var turn = 0
  private var turnCount = 0
  private var rotation = Rotation.Clockwise
  private var deck = UnoDeck()
  private var played = UnoDeck(empty = true)
  private lateinit var color: UnoColor
  private var mustDraw: MustDraw? = null
    
  // History
  private val _turns = mutableListOf<Pair<String, Turn>>()
  val turns: List<Pair<String, Turn>> = _turns
  //private val initialDeck = deck.snapshot()
  
  init {
    require(players.size == 4) {
      "Only 4-player games are currently supported" 
    }
    var firstCard = deck.draw()
    while (firstCard !is UnoCard.Colored) {
      deck.pushUnder(firstCard)
      firstCard = deck.draw()
    }
    played.push(firstCard)
    color = firstCard.color
    repeat(7) {
      players.forEach(::giveCard)
    }
  }
  
  fun play(): Player {
    while(players.all { it.hand.isNotEmpty() }) {
      val player = players[turn]
      val turn = player.turn(
        lastCard = played.peek(),
        rotation = rotation,
        color = color,
        mustDraw = mustDraw,
      )
      when(turn) {
        is Turn.Play -> {
           val card = turn.card
           player.play(card)
           played.push(card)
           color = card.color
           if (card is UnoCard.Reverse) {
             reverse()  
           }
           if (card is UnoCard.Skip) {
             nextTurn()
           }
           if (card is UnoCard.Draw2) {
             mustDraw = mustDraw?.let {
               it.copy(cards = it.cards + 2)
             } ?: MustDraw(
               factor = DrawFactor.Two,
               cards = 2,
             )
           }
        }
        is Turn.PlayWildcard -> {
           val card = turn.card
           player.play(card)
           played.push(card)
           color = turn.newColor
           if (card is UnoCard.Draw4) {
             mustDraw = mustDraw?.let {
               it.copy(
                 factor = DrawFactor.Four,
                 cards = it.cards + 4,
               )
             } ?: MustDraw(
               factor = DrawFactor.Four,
               cards = 4,
             )
           }
        }
        is Turn.Draw -> {
          repeat(mustDraw?.cards ?: 1) {
            if (deck.isEmpty()) {
              deck = played.clone()
              if (debug) {
                println("[RESET] deck(${deck.size}); played(${played.size})")
              }
              played = UnoDeck(empty = true).also {
               it.push(deck.draw()) // restore top
              }
              deck.shuffle()
            }
            giveCard(player)
          }
          mustDraw = null
        }
      }
      _turns.add(player.name to turn)
      val totalCards = deck.size + played.size + players.sumOf {
        it.hand.size
      }
      require(totalCards == 108) {
        "Cards invariant broken! Total cards = $totalCards"  
      }
      if (debug) {
        println("${player.name} (${player.hand.size}): $turn deck(${deck.size})")
      }
      nextTurn()
    }
    val winner = players.first { it.hand.isEmpty() }
    return winner
  }
  
  private fun nextTurn() {
    when(rotation) {
      Rotation.Clockwise -> {
        turn = (turn + 1) % players.size
      }
      Rotation.CounterClockwise -> {
        turn--
        if (turn < 0) {
          turn = players.lastIndex
        }
      }
    }
    turnCount++
  }
  
  private fun reverse() {
    rotation = when(rotation) {
      Rotation.Clockwise -> Rotation.CounterClockwise
      Rotation.CounterClockwise -> Rotation.Clockwise
    }
  }
  
  private fun giveCard(player: Player) {
    player.draw(deck.draw())
  }
}

class DumbStrategy : Strategy {
  override fun turn(
    hand: List<UnoCard>,
    lastCard: UnoCard,
    rotation: Rotation,
    color: UnoColor,
    mustDraw: MustDraw?,
  ): Turn {
    val card = hand.firstOrNull {
      validTurn(
        card = it,
        lastCard = lastCard,
        color = color,
        mustDraw = mustDraw,
      )
    }
    return when(card) {
      is UnoCard.Colored -> Turn.Play(card)
      is UnoCard.Wildcard -> Turn.PlayWildcard(card, color)
      else -> Turn.Draw
    }
  }
}

fun validTurn(
  card: UnoCard,
  mustDraw: MustDraw?,
  color: UnoColor,
  lastCard: UnoCard,
): Boolean {
   return when(card) {
        is UnoCard.Colored -> if (mustDraw != null) {
          card is UnoCard.Draw2 && mustDraw.factor == DrawFactor.Two
        } else {
          card.color == color ||
          (card is UnoCard.Number && 
             lastCard is UnoCard.Number &&
             card.n == lastCard.n) ||
          (card is UnoCard.Reverse && 
             lastCard is UnoCard.Reverse) ||
          (card is UnoCard.Skip &&
             lastCard is UnoCard.Skip)    
        }
        is UnoCard.Wildcard -> 
          mustDraw == null || card is UnoCard.Draw4
      }
}

fun main() {
  simulate(
    ps = listOf(
      "dumb1" to DumbStrategy(),
      "dumb2" to DumbStrategy(),
      "random3" to RandomStrategy(),
      "random4" to RandomStrategy(),
    ),
    //games = 1,
    //shufflePlayers = false,
  )
}

fun simulate(
 ps: List<Pair<String, Strategy>>,
 games: Int = 50_000,
 shufflePlayers: Boolean = true,
) {
   println("$games games, shuffled=$shufflePlayers")
   val wins = mutableMapOf<String, Int>()
   ps.forEach { p ->
     wins[p.first] = 0
   }
   var totalTurns = 0
   var winnerStats = HandStats()
   var losersStats = HandStats()
   repeat(games) {
     val players = (if (shufflePlayers) 
       ps.shuffled()
     else
       ps
     ).map { (name, strategy) ->
       Player(
         name = name,
         strategy = strategy,
       )
     }
     val game = UnoGame(
      players = players,
     )
     val winner = game.play()
     wins[winner.name] = wins[winner.name]!! + 1
     totalTurns += game.turns.size
     winnerStats += HandStats(winner.history[0].first)
     players.filter { it.name != winner.name }
       .forEach { loser ->
         losersStats += HandStats(loser.history[0].first)
       }
  }
  println(
    wins
      .map { (p, w) ->
        w to "$p: $w wins (${(w.toFloat()/games).toString().take(5)})"
      }
      .sortedByDescending { (w, _) -> w }
      .map { (_, s) -> s }
      .joinToString(separator="\n")
  )
  val avgGameTurns = totalTurns / games
  val avgPlayerTurns = avgGameTurns / ps.size
  println("Avg $avgGameTurns turns in a game ($avgPlayerTurns per player)")
  val avgWinnerStats = winnerStats.divide(games)
  println("Winner: $avgWinnerStats")
  val avgLoserStats = losersStats.divide(games).divide(ps.size - 1)
  println("Loser: $avgLoserStats")
}

data class HandStats(
  val special: Float = 0f,
  val number: Float = 0f,
  val draw2: Float = 0f,
  val draw4: Float = 0f,
  val changeColor: Float = 0f,
  val skip: Float = 0f,
  val reverse: Float = 0f,
  val colors: Float = 0f,
  val colorStreak: Float = 0f,
  val sameNumbers: Float = 0f,
) {
  constructor(hand: List<UnoCard>) : this(
    special = hand.count { it !is UnoCard.Number }.toFloat(),
    number = hand.countByCardType<UnoCard.Number>(),
    draw2 = hand.countByCardType<UnoCard.Draw2>(),
    draw4 = hand.countByCardType<UnoCard.Draw4>(),
    changeColor = hand.countByCardType<UnoCard.ChangeColor>(),
    skip = hand.countByCardType<UnoCard.Skip>(),
    reverse = hand.countByCardType<UnoCard.Reverse>(),
    colors = hand.mapNotNull {
      (it as? UnoCard.Colored)?.color
    }.toSet().size.toFloat(),
    colorStreak = hand.maxCountBy {
      (it as? UnoCard.Colored)?.color
    }.toFloat(),
    sameNumbers = hand.maxCountBy {
      (it as? UnoCard.Number)?.n
    }.toFloat(),
  )
  
  operator fun plus(other: HandStats): HandStats {
    return HandStats(
      special = special + other.special,
      number = number + other.number,
      draw2 = draw2 + other.draw2,
      draw4 = draw4 + other.draw4,
      changeColor = changeColor + other.changeColor,
      skip = skip + other.skip,
      reverse = reverse + other.reverse,
      colors = colors + other.colors,
      colorStreak = colorStreak + other.colorStreak,
      sameNumbers = sameNumbers + other.sameNumbers,
    )
  }
  
  fun divide(n: Int): HandStats {
    return HandStats(
      special = special / n,
      number = number / n,
      draw2 = draw2 / n,
      draw4 = draw4 / n,
      changeColor = changeColor / n,
      skip = skip / n,
      reverse = reverse / n,
      colors = colors / n,
      colorStreak = colorStreak / n,
      sameNumbers = sameNumbers / n,
    )
  }
  
  override fun toString(): String {
    return "HandStats(" +
    "${special.format()} special, " +
    "${number.format()} Number, " +
    "${draw2.format()} Draw2, " +
    "${draw4.format()} Draw4, " +
    "${(draw2 + draw4).format()} total draw, " +
    "${changeColor.format()} ChangeColor, " +
    "${skip.format()} Skip, " +
    "${reverse.format()} Reverse, " +
    "${colors.format()} colors, " +
    "${colorStreak.format()} of same color, " +
    "${sameNumbers.format()} same numbers" +
    ")"
  }
  
  companion object {
    private inline fun <reified T: UnoCard> List<UnoCard>.countByCardType(): Float {
      return this.count { it is T }.toFloat()
    }
    
    private fun <T> List<UnoCard>.maxCountBy(
      selector: (UnoCard) -> T,
    ): Int {
      return this.groupingBy(selector)
       .eachCount()
       .maxByOrNull { entry -> entry.value }
       ?.value ?: 0
    } 
  }
}

class RandomStrategy : Strategy {
  override fun turn(
    hand: List<UnoCard>,
    lastCard: UnoCard,
    rotation: Rotation,
    color: UnoColor,
    mustDraw: MustDraw?,
  ): Turn {
    val card = hand.filter {
      validTurn(
        card = it,
        lastCard = lastCard,
        color = color,
        mustDraw = mustDraw,
      )
    }.takeIf { it.isNotEmpty() }?.random()
    return when(card) {
      is UnoCard.Colored -> Turn.Play(card)
      is UnoCard.Wildcard -> Turn.PlayWildcard(
        card = card, 
        newColor = dominantColor(hand),
      )
      else -> Turn.Draw
    }
  }
}

fun dominantColor(
  cards: List<UnoCard>
): UnoColor {
   val count = buildMap {
     UnoColor.entries.forEach { color ->
       put(color, 0)
     }
   }.toMutableMap()
   cards.forEach { card ->
     if (card is UnoCard.Colored) {
       count[card.color] = count[card.color]!! + 1
     }
   }
   return count.maxBy { (_, c) -> c }.key
}

private fun Float.format(
  decimalPlaces: Int = 2,
): String {
  return String.format("%.${decimalPlaces}f", this)
}
