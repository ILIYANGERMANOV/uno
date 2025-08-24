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
  
  fun snapshot(): UnoDeck {
    return UnoDeck(empty = true).apply {
      cards.forEach { push(it) }
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
    
   fun turn(
     lastCard: UnoCard,
     rotation: Rotation,
     color: UnoColor,
     mustDraw: MustDraw?,
   ): Turn {
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
           require(mustDraw.factor == DrawFactor.Two) {
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
           }
         ) {
           "Can't draw when you can match the color!"
         }
       }
     }
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
     // Same type
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
  players: List<Player>
) {
  private val players = players.shuffled()
  private var turn = 0
  private var turnCount = 0
  private var rotation = Rotation.Clockwise
  private var deck = UnoDeck()
  private val played = UnoDeck(empty = true)
  private lateinit var color: UnoColor
  private var mustDraw: MustDraw? = null
    
  // History
  private val turns = mutableListOf<Pair<String, Turn>>()
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
  }
  
  fun play() {
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
              deck = played
              deck.draw() // discard top
            }
            player.draw(deck.draw())
          }
        }
      }
      turns.add(player.name to turn)
      nextTurn()
    }
    val winner = players.first { it.hand.isEmpty() }
    println(winner.name)
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
}

class RandomStrategy : Strategy {
  override fun turn(
    hand: List<UnoCard>,
    lastCard: UnoCard,
    rotation: Rotation,
    color: UnoColor,
    mustDraw: MustDraw?,
  ): Turn {}
}

fun main() {
  val deck = UnoDeck()
  println(deck)
  println(deck.draw())
  println(deck)
}
