package com.wepoker.domain.algorithm;

import com.wepoker.domain.model.Card;
import com.wepoker.domain.model.HandRank;
import com.wepoker.domain.model.Rank;
import com.wepoker.domain.model.Suit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandEvaluatorTest {

    @Test
    void straightFlushBeatsFourOfAKind() {
        Card[] straightFlush = cards(
            c(Suit.SPADE, Rank.ACE),
            c(Suit.SPADE, Rank.KING),
            c(Suit.SPADE, Rank.QUEEN),
            c(Suit.SPADE, Rank.JACK),
            c(Suit.SPADE, Rank.TEN),
            c(Suit.CLUB, Rank.TWO),
            c(Suit.DIAMOND, Rank.THREE)
        );

        Card[] fourKind = cards(
            c(Suit.SPADE, Rank.ACE),
            c(Suit.HEART, Rank.ACE),
            c(Suit.DIAMOND, Rank.ACE),
            c(Suit.CLUB, Rank.ACE),
            c(Suit.DIAMOND, Rank.KING),
            c(Suit.CLUB, Rank.TWO),
            c(Suit.HEART, Rank.THREE)
        );

        HandRank r1 = HandEvaluator.evaluateSevenCards(straightFlush);
        HandRank r2 = HandEvaluator.evaluateSevenCards(fourKind);

        assertEquals(8, r1.getHandType());
        assertEquals(7, r2.getHandType());
        assertTrue(HandEvaluator.compareHands(r1, r2) > 0);
    }

    @Test
    void fullHouseBeatsFlush() {
        Card[] fullHouse = cards(
            c(Suit.SPADE, Rank.KING),
            c(Suit.HEART, Rank.KING),
            c(Suit.DIAMOND, Rank.KING),
            c(Suit.CLUB, Rank.TWO),
            c(Suit.DIAMOND, Rank.TWO),
            c(Suit.HEART, Rank.NINE),
            c(Suit.CLUB, Rank.TEN)
        );

        Card[] flush = cards(
            c(Suit.HEART, Rank.ACE),
            c(Suit.HEART, Rank.KING),
            c(Suit.HEART, Rank.NINE),
            c(Suit.HEART, Rank.FIVE),
            c(Suit.HEART, Rank.THREE),
            c(Suit.CLUB, Rank.TWO),
            c(Suit.DIAMOND, Rank.FOUR)
        );

        HandRank r1 = HandEvaluator.evaluateSevenCards(fullHouse);
        HandRank r2 = HandEvaluator.evaluateSevenCards(flush);

        assertEquals(6, r1.getHandType());
        assertEquals(5, r2.getHandType());
        assertTrue(HandEvaluator.compareHands(r1, r2) > 0);
    }

    @Test
    void wheelStraightBeatsThreeOfAKind() {
        Card[] wheelStraight = cards(
            c(Suit.CLUB, Rank.ACE),
            c(Suit.DIAMOND, Rank.TWO),
            c(Suit.HEART, Rank.THREE),
            c(Suit.SPADE, Rank.FOUR),
            c(Suit.CLUB, Rank.FIVE),
            c(Suit.DIAMOND, Rank.KING),
            c(Suit.HEART, Rank.QUEEN)
        );

        Card[] trips = cards(
            c(Suit.CLUB, Rank.NINE),
            c(Suit.DIAMOND, Rank.NINE),
            c(Suit.HEART, Rank.NINE),
            c(Suit.SPADE, Rank.ACE),
            c(Suit.CLUB, Rank.KING),
            c(Suit.DIAMOND, Rank.TWO),
            c(Suit.HEART, Rank.THREE)
        );

        HandRank r1 = HandEvaluator.evaluateSevenCards(wheelStraight);
        HandRank r2 = HandEvaluator.evaluateSevenCards(trips);

        assertEquals(4, r1.getHandType());
        assertEquals(3, r2.getHandType());
        assertTrue(HandEvaluator.compareHands(r1, r2) > 0);
    }

    @Test
    void pairKickerComparisonWorks() {
        Card[] pairAWithKingKicker = cards(
            c(Suit.SPADE, Rank.ACE),
            c(Suit.HEART, Rank.ACE),
            c(Suit.CLUB, Rank.KING),
            c(Suit.HEART, Rank.SEVEN),
            c(Suit.SPADE, Rank.FOUR),
            c(Suit.DIAMOND, Rank.TWO),
            c(Suit.CLUB, Rank.THREE)
        );

        Card[] pairAWithQueenKicker = cards(
            c(Suit.DIAMOND, Rank.ACE),
            c(Suit.CLUB, Rank.ACE),
            c(Suit.HEART, Rank.QUEEN),
            c(Suit.DIAMOND, Rank.SEVEN),
            c(Suit.HEART, Rank.FOUR),
            c(Suit.SPADE, Rank.TWO),
            c(Suit.DIAMOND, Rank.THREE)
        );

        HandRank r1 = HandEvaluator.evaluateSevenCards(pairAWithKingKicker);
        HandRank r2 = HandEvaluator.evaluateSevenCards(pairAWithQueenKicker);

        assertEquals(1, r1.getHandType());
        assertEquals(1, r2.getHandType());
        assertTrue(HandEvaluator.compareHands(r1, r2) > 0);
    }

    @Test
    void compareHandsReturnsZeroForTie() {
        Card[] handA = cards(
            c(Suit.SPADE, Rank.ACE),
            c(Suit.HEART, Rank.KING),
            c(Suit.DIAMOND, Rank.QUEEN),
            c(Suit.CLUB, Rank.JACK),
            c(Suit.SPADE, Rank.NINE),
            c(Suit.HEART, Rank.FOUR),
            c(Suit.DIAMOND, Rank.TWO)
        );

        Card[] handB = cards(
            c(Suit.CLUB, Rank.ACE),
            c(Suit.DIAMOND, Rank.KING),
            c(Suit.SPADE, Rank.QUEEN),
            c(Suit.HEART, Rank.JACK),
            c(Suit.CLUB, Rank.NINE),
            c(Suit.SPADE, Rank.FOUR),
            c(Suit.HEART, Rank.TWO)
        );

        HandRank r1 = HandEvaluator.evaluateSevenCards(handA);
        HandRank r2 = HandEvaluator.evaluateSevenCards(handB);

        assertEquals(HandEvaluator.compareHands(r1, r2), 0);
    }

    @Test
    void evaluateSevenCardsRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> HandEvaluator.evaluateSevenCards(new Card[6]));

        Card[] hasNull = new Card[] {
            c(Suit.SPADE, Rank.ACE),
            c(Suit.HEART, Rank.KING),
            c(Suit.DIAMOND, Rank.QUEEN),
            c(Suit.CLUB, Rank.JACK),
            c(Suit.SPADE, Rank.TEN),
            c(Suit.HEART, Rank.NINE),
            null
        };
        assertThrows(IllegalArgumentException.class, () -> HandEvaluator.evaluateSevenCards(hasNull));
    }

    private static Card c(Suit suit, Rank rank) {
        return Card.of(suit, rank);
    }

    private static Card[] cards(Card... cards) {
        return cards;
    }
}
