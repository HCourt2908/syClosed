package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import javax.annotation.Nonnull;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;
import java.util.*;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {

	private final class MyGameState implements GameState {

		private final GameSetup setup;
		//holds the current game state
		private final ImmutableSet<Piece> remaining;
		//holds the pieces of every player that is yet to move in the current turn
		private final ImmutableList<LogEntry> log;
		//a list of all moves that mr X has made so far
		private final Player mrX;
		//the player mr X
		private final List<Player> detectives;
		//a list of detective players
		private final ImmutableSet<Move> moves;
		//a set of all possible moves the players in remaining can make
		private final ImmutableSet<Piece> winner;
		//a set containing the player(s) (if any) who won the game

		private Player getPlayerFromPiece(Piece piece) {
			//method that finds a player from all the pieces
			if (piece.isMrX()) {
				return mrX;
			} else {
				for (Player detective : detectives) {
					//if not mr x, we know it's a detective. loop over all detectives in list.
					if (detective.piece() == piece) {
						return detective;
					}
				}
			}
			return null;
			//returns null if player is neither a detective nor mr X. this may happen if not all detectives are in play
		}

		private boolean validMoveCheck(int destination, ArrayList<ScotlandYard.Ticket> validTicketTypes) {
			//some valid move checks were identical in both makeSingleMoves and makeDoubleMoves, so put them all in one method

			boolean validMove = detectives.stream().reduce(true,
					(validSoFar, element) -> validSoFar && (!(element.location()==destination)),
					(element1, element2) -> (element1&&element2));

			//the reduce method is like foldr. the list of detectives is passed in as a stream.
			//the identity 'true' is like the base case of haskell's foldr, or the default value

			//the first lambda expression ANDS together the validSoFar boolean and whether the current detective
			//in the detectives list is currently in destination

			//the second lambda isn't actually needed, since I am not doing parallel streaming, but the compiler
			//needs it anyway
			//it just ANDS together the two given booleans

			//TL:DR this takes all detectives, returns true if none of them are located at destination, false otherwise

			validMove = validMove && !validTicketTypes.isEmpty();

			return validMove;

		}

		private ImmutableSet<Move> makeAllMoves() {

			HashSet<Move> possibleMoves = new HashSet<>();
			//stores all possible moves that can be made
			//it needs to be non-immutable because I add single and double moves at different points in time.
			//once all the possibleMoves are found, I can then make an immutable copy of it into moves

			if (remaining.contains(mrX.piece())) {
				//if mrX hasn't made a move yet
				possibleMoves.addAll(makeSingleMoves(setup,detectives, mrX, mrX.location()));
				//adds all possible single moves

				if(mrX.has(ScotlandYard.Ticket.DOUBLE) && setup.moves.size() - log.size() > 1) {
					//if mrX has a double ticket remaining

					Set<Move.DoubleMove> possibleDoubleMoves = new HashSet<>();

					for (Move move : possibleMoves) {
						//for every possible single move, find all possible double moves

						possibleDoubleMoves.addAll(makeDoubleMoves(setup, detectives, mrX, (Move.SingleMove) move));
						//add them all into the list

					}

					possibleMoves.addAll(possibleDoubleMoves);
				}
			} else {
				for (Piece detectivePiece : remaining) {
					//if mrX moved already, list should contain all possible moves for all detectives

					Player detectivePlayer = getPlayerFromPiece(detectivePiece);
					possibleMoves.addAll(makeSingleMoves(setup, detectives, detectivePlayer, detectivePlayer.location()));
					//add all into a list
				}
			}

			return ImmutableSet.copyOf(possibleMoves);

		}

		private Set<Move.SingleMove> makeSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {

			HashSet<Move.SingleMove> availableMoves = new HashSet<>();
			//creates a new hashset to hold all doable moves

			for(int destination : setup.graph.adjacentNodes(source)) {
				//iterates over all possible single moves

				TicketBoard playerTickets = getPlayerTickets(player.piece()).get();
				//gets a ticketBoard of all the player's tickets
				ArrayList<ScotlandYard.Ticket> validTicketTypes = new ArrayList<>();
				//creates an array to store all valid modes of transport

				for (ScotlandYard.Transport t : setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of())) {
					//iterates over the set of possible transport methods to get from source to destination

					if (playerTickets.getCount(t.requiredTicket()) > 0) {
						//if the player has at least one of the correct ticket
						validTicketTypes.add(t.requiredTicket());
						//add possible ticket to the validTicketTypes list
					}

				}

				if (playerTickets.getCount(ScotlandYard.Ticket.SECRET) >0) {
					//if the player has a secret ticket then by default every move is valid
					validTicketTypes.add(ScotlandYard.Ticket.SECRET);
				}

				if (validMoveCheck(destination, validTicketTypes)) {
					for (ScotlandYard.Ticket validTicket : validTicketTypes) {
						//adds a possible move for all valid ticket types
						availableMoves.add(new Move.SingleMove(player.piece(), source, validTicket, destination));
					}
				}
			}


			return availableMoves;

		}

		private Set<Move.DoubleMove> makeDoubleMoves(GameSetup setup, List<Player> detectives, Player player, Move.SingleMove firstMove) {

			HashSet<Move.DoubleMove> availableMoves = new HashSet<>();
			//initialises set to contain all valid moves

			for (int destination : setup.graph.adjacentNodes(firstMove.destination)) {
				//iterates over every node adjacent to the destination node of the first move

				TicketBoard playerTickets = getPlayerTickets(player.piece()).get();
				//gets the player's tickets
				ArrayList<ScotlandYard.Ticket> validTicketTypes = new ArrayList<>();
				//makes an arrayList to hold all valid tickets that can be used

				for (ScotlandYard.Transport t: setup.graph.edgeValueOrDefault(firstMove.destination, destination, ImmutableSet.of())) {
					//iterate over all possible methods of transport to get from destination of first move to destination of second

					if (firstMove.ticket == t.requiredTicket() && playerTickets.getCount(t.requiredTicket()) > 1) {
						//this checks that a double move can't be made with the same ticket both times if only has one ticket of that type
						validTicketTypes.add(t.requiredTicket());
					} else if (firstMove.ticket != t.requiredTicket() && playerTickets.getCount(t.requiredTicket()) > 0){
						//the ticket for the second move is a different one to the first one, and we already know we have a ticket for first move
						validTicketTypes.add(t.requiredTicket());
					}
					//if the two tickets are the same, and we don't have >= two tickets of the same type, don't add anything

				}

				if (firstMove.ticket == ScotlandYard.Ticket.SECRET && playerTickets.getCount(ScotlandYard.Ticket.SECRET) > 1) {
					//if the first move used a ticket, and the player has more than one secret ticket, add secret
					validTicketTypes.add(ScotlandYard.Ticket.SECRET);
				} else if (firstMove.ticket != ScotlandYard.Ticket.SECRET && playerTickets.getCount(ScotlandYard.Ticket.SECRET) > 0) {
					//if the first move didn't use a secret ticket and the player has at least one secret ticket, add secret
					validTicketTypes.add(ScotlandYard.Ticket.SECRET);
				}
				//if the two tickets are secret, and we don't have >= two secret tickets, don't add anything

				if (validMoveCheck(destination, validTicketTypes)) {

					for (ScotlandYard.Ticket validTicket : validTicketTypes) {
						availableMoves.add(new Move.DoubleMove(player.piece(), firstMove.source(), firstMove.ticket, firstMove.destination, validTicket, destination));
						//creates a double move for each valid ticket type, using the information we have gathered in this method
					}

				}

			}


			return availableMoves;
		}

		private void checkDetectiveTickets() {
			//checks if detectives have any secret or double tickets, if so throw an error
			for (Player detective : detectives) {
				if (detective.has(ScotlandYard.Ticket.SECRET)) {
					throw new IllegalArgumentException("Detectives should not have secret tickets");
				} else if (detective.has(ScotlandYard.Ticket.DOUBLE)) {
					throw new IllegalArgumentException("Detectives should not have double tickets");
				}
			}
		}

		private void checkDetectiveLocations() {
			//ensures that all detectives are on unique locations
			ArrayList<Integer> locations = new ArrayList<>();
			//holds the location of all detectives
			for(Player detective : detectives) {

				if (locations.contains(detective.location())) throw new IllegalArgumentException(
						"more than one detective is in the same location");
				locations.add(detective.location());	//if unique, adds new detective location

			}
		}

		private boolean checkMrXWin() {
			boolean mrXWin = false;
			//mr x wins if:

			if (remaining.contains(mrX.piece())) {
				boolean locationFlag = true;
				for (Player detective : detectives) {
					if (mrX.location() == detective.location()) locationFlag = false;
				}

				if (locationFlag && log.size() == setup.moves.size()) mrXWin = true;
				//if mr x has used all his moves and a detective is not on top of him
			}

			if (!(remaining.contains(mrX.piece())) && moves.isEmpty()) {
				//if remaining doesn't contain mr X then it will contain at least one detective
				mrXWin = true;
			}
			//the detectives cannot move any of their subsequent pieces

			boolean noTicketsFlag = true;
			for (Player detective : detectives) {
				if (hasAnyTickets(detective)) noTicketsFlag = false;
			}
			if (noTicketsFlag) mrXWin = true;
			//if all detectives have no tickets, then no detective can move -> mr x wins

			return mrXWin;
		}

		private boolean checkDetectivesWin() {
			boolean detectiveWin = false;

			//detectives win if:
			for (Player detective : detectives) {
				if (detective.location() == mrX.location()) {
					detectiveWin = true;
				}
			}
			//a detective is in the same station as mr x

			if (remaining.contains(mrX.piece()) && moves.isEmpty()) {
				//if it is MrX's turn, but he cannot make any moves -> detectives win
				detectiveWin = true;
			}

			if (!hasAnyTickets(mrX)) detectiveWin = true;
			//if mr x has no tickets, mr x cant move and the detectives win

			return detectiveWin;
		}

		private Set<Piece> getDetectiveSet() {	//returns a set of all detective pieces
			Set<Piece> detectiveSet = new HashSet<>();
			for (Player detective : detectives) {
				detectiveSet.add(detective.piece());
			}
			return detectiveSet;
		}


		private MyGameState(		//constructor to make a game state
				final GameSetup setup,
				final ImmutableSet<Piece> remaining,
				final ImmutableList<LogEntry> log,
				final Player mrX,
				final List<Player> detectives) {

			if (setup.moves.isEmpty()) {
				//if the list of moves provided is empty -> a game of length zero moves makes no sense
				throw new IllegalArgumentException("Moves list provided in setup is empty");
			} else if (setup.graph.nodes().isEmpty()) {
				//if the graph of all moves is empty
				throw new IllegalArgumentException("Graph provided in setup is empty");
			} else {
				this.setup = setup;
			}

			if (remaining == null) {
				throw new IllegalArgumentException("Provided remaining is null");
			} else {
				this.remaining = remaining;
			}

			this.log = log;


			if (mrX == null) {
				throw new NullPointerException("Null MrX provided.");
			} else if (!mrX.isMrX()) {
				//checks if the given MrX is actually a detective (isn't black piece)
				throw new IllegalArgumentException("MrX provided is a detective");
			} else {
				this.mrX = mrX;
			}

			if (detectives == null) {
				throw new NullPointerException(("Null detectives list provided"));
			} else {
				this.detectives = detectives;
			}

			for (Player player : detectives) {
				//checks all detectives to see if any is a mrX (black piece)
				if (player.isMrX()) throw new IllegalArgumentException("MrX provided as a detective");
            }

			checkDetectiveTickets();
			//checks the tickets of the detectives for validity
			checkDetectiveLocations();
			//checks that all detectives are in a unique location

			this.moves = makeAllMoves();

			boolean detectiveWin = checkDetectivesWin();
			boolean mrXWin = checkMrXWin();

			if (mrXWin && detectiveWin) throw new IllegalArgumentException("Mr X and detectives can't both win");
			else if (mrXWin) this.winner = ImmutableSet.of(mrX.piece());
			else if (detectiveWin) this.winner = ImmutableSet.copyOf(getDetectiveSet());
			else this.winner = ImmutableSet.of();
			//winner is an empty set if neither mr X nor detectives have won

		}


		@Nonnull
		@Override
		public GameSetup getSetup() {
			return setup;
		}	//returns setup

		@Nonnull
		@Override
		public ImmutableSet<Piece> getPlayers() {
			//returns an immutable set of mrX plus all detectives
			HashSet<Piece> playerSet = new HashSet<>();
			//creates a set to hold players
			playerSet.add(mrX.piece());
			for (Player player : detectives) {
				playerSet.add(player.piece());
			}
			return ImmutableSet.copyOf(playerSet);
		}

		@Nonnull
		@Override
		public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
			//gets location of specified detective
			Player detectivePlayer = getPlayerFromPiece(detective);
			if (detectivePlayer != null) return Optional.of(detectivePlayer.location());
			else return Optional.empty();
		}

		@Nonnull
		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			//gets the tickets of the provided piece

			try {
				//if getPlayerFromPiece fails, it will return null, causing an exception when tickets method is called
				//this will then go to the catch block where an optional.empty is returned
				ImmutableMap<ScotlandYard.Ticket, Integer> playerTickets = getPlayerFromPiece(piece).tickets();
				return Optional.of(new TicketBoard() {
					@Override
					public int getCount(@Nonnull ScotlandYard.Ticket ticket) {
						if (ticket == ScotlandYard.Ticket.TAXI) return playerTickets.get(ScotlandYard.Ticket.TAXI);
						else if (ticket == ScotlandYard.Ticket.BUS) return playerTickets.get(ScotlandYard.Ticket.BUS);
						else if (ticket == ScotlandYard.Ticket.UNDERGROUND) return playerTickets.get(ScotlandYard.Ticket.UNDERGROUND);
						else if (ticket == ScotlandYard.Ticket.DOUBLE) return playerTickets.get(ScotlandYard.Ticket.DOUBLE);
						else if (ticket == ScotlandYard.Ticket.SECRET) return playerTickets.get(ScotlandYard.Ticket.SECRET);
						else return 0;
					}
				});
			} catch (Exception e) {
				return Optional.empty();
			}

		}

		@Nonnull
		@Override
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return log;
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getWinner() {
			return winner;
		}

		@Nonnull
		@Override
		public ImmutableSet<Move> getAvailableMoves() {
			if (winner.isEmpty()) return moves;
			else return ImmutableSet.of();
		}

		public boolean hasAnyTickets(Player player) {
			//helper function -> returns false if the player has NO tickets of any type
			return (player.has(ScotlandYard.Ticket.TAXI) ||
					player.has(ScotlandYard.Ticket.BUS) ||
					player.has(ScotlandYard.Ticket.UNDERGROUND) ||
					player.has(ScotlandYard.Ticket.SECRET) ||
					player.has(ScotlandYard.Ticket.DOUBLE));
		}

		public GameState handleSingleMoves(Move.SingleMove move) {
			//returns a GameState, updated after the single move takes place

			Set<Piece> newStateRemaining = new HashSet<>();

			Piece mover = move.commencedBy();


			if (mover == mrX.piece()) {

				LogEntry newLogEntry;
				List<LogEntry> newStateLog = new ArrayList<>();
				//log is immutable, so we need to make a new list to contain the log for the new state
				newStateLog.addAll(log);
				// we have to copy over the current log entries.

				//if mrx:
				Player newMrX = mrX.at(move.destination);
				newMrX = newMrX.use(move.ticket);
				//made a new MrX player but with an updated location (location is immutable) and tickets


				//in setup.moves, if the index is a true then it should make a revealed log. if not, then make a hidden log.
				if (setup.moves.get(log.size())) {
					newLogEntry = LogEntry.reveal(move.ticket, move.destination);
				} else {
					newLogEntry = LogEntry.hidden(move.ticket);
				}
				newStateLog.add(newLogEntry);
				//add the new move log to the new log list

				for(Player detective : detectives) {
					if (hasAnyTickets(detective) )newStateRemaining.add(detective.piece());
				}
				//removed mr X from the remaining players set and added all detectives

				return new MyGameState(setup, ImmutableSet.copyOf(newStateRemaining), ImmutableList.copyOf(newStateLog), newMrX, detectives);

			} else {

				Player newDetective = getPlayerFromPiece(mover).at(move.destination);
				newDetective = newDetective.use(move.ticket);
				//makes a new detective with an updated location and tickets

				Player newMrX = mrX.give(move.ticket);
				//give the used ticket to mrX

				List<Player> newDetectives = new ArrayList<>();
				//we need to make a new list of detectives, with the old mover replaced by the new one
				for (Player detective : detectives) {
					if (!(detective.piece() == mover)) newDetectives.add(detective);
				}
				newDetectives.add(newDetective);

				if (remaining.size() != 1 ) {
					//if there is at least one other detective left to move in this turn
					for(Piece player : remaining) {
						//if the player hasn't just made a move, and they have at least one ticket
						if (player != mover && hasAnyTickets(getPlayerFromPiece(player))) newStateRemaining.add(player);
					}
				} else {
					newStateRemaining.add(mrX.piece());
				}

				return new MyGameState(setup, ImmutableSet.copyOf(newStateRemaining), log, newMrX, newDetectives);

			}
		}

		public GameState handleDoubleMoves(Move.DoubleMove move) {		//returns a player, updated after the double move takes place

			//if mrx:
			Player newMrX = mrX.at(move.destination2);
			newMrX = newMrX.use(move.ticket1);
			newMrX = newMrX.use(move.ticket2);
			newMrX = newMrX.use(ScotlandYard.Ticket.DOUBLE);
			//move mr x location

			LogEntry logEntry1;
			LogEntry logEntry2;
			if (setup.moves.get(log.size())) {
				logEntry1 = LogEntry.reveal(move.ticket1, move.destination1);
			} else {
				logEntry1 = LogEntry.hidden(move.ticket1);
			}
			//creates the log entry for the first of the two single moves that make the double move

			if (setup.moves.get(log.size()+1)) {
				logEntry2 = LogEntry.reveal(move.ticket2, move.destination2);
			} else {
				logEntry2 = LogEntry.hidden(move.ticket2);
			}
			//creates the log entry for the second of the two single moves that make the double move

			List<LogEntry> newDoubleStateLog = new ArrayList<>();
			newDoubleStateLog.addAll(log);
			newDoubleStateLog.add(logEntry1);
			newDoubleStateLog.add(logEntry2);
			//creates a new list to hold the log entries, copies over the old entries and adds the two new ones

			Set<Piece> newDoubleStateRemaining = new HashSet<>();
			for (Player detective : detectives) {
				if (hasAnyTickets(detective)) newDoubleStateRemaining.add(detective.piece());
			}
			//updates remaining. I don't need to do as much checking here since I know that remaining must contain only MrX
			//this is because only mr X can have double tickets, so only mr X can make double moves

			return new MyGameState(setup, ImmutableSet.copyOf(newDoubleStateRemaining), ImmutableList.copyOf(newDoubleStateLog), newMrX, detectives);
		}

		@Nonnull
		@Override
		public GameState advance(Move move) {
			if (!moves.contains(move)) throw new IllegalArgumentException("Illegal move: " + move);
			//if an invalid move is trying to be made

			//we want to find the type of move, single or double
			Move.Visitor<GameState> moveVisitor = new Move.Visitor<>() {
				//make a new move visitor which does different things depending on
				//whether the supplied move is single or double
				@Override
				public GameState visit(Move.SingleMove move) {
					return handleSingleMoves(move);
				}

				@Override
				public GameState visit(Move.DoubleMove move) {
					return handleDoubleMoves(move);
				}
			};

            return move.accept(moveVisitor);
			//move.accept returns a new gameState
		}
	}


	@Nonnull @Override public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives) {
		return new MyGameState(setup, ImmutableSet.of(Piece.MrX.MRX), ImmutableList.of(), mrX, detectives);
		//makes a new gameState

	}

}
