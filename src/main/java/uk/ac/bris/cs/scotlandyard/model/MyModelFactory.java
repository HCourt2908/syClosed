package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import java.util.HashSet;
import java.util.Set;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {

	@Nonnull @Override public Model build(GameSetup setup,
	                                      Player mrX,
	                                      ImmutableList<Player> detectives) {
		// TODO

		return new Model() {

			private Set<Observer> observers = new HashSet<>();
			//creates a set to contain all registered observers
			private MyGameStateFactory stateFactory = new MyGameStateFactory();
			//creates a new game state factory to create all the
			private Board.GameState state = stateFactory.build(setup, mrX, detectives);
			//creates the initial game state using the created state factory


			@Nonnull
			@Override
			public Board getCurrentBoard() {
				return state;
			}

			@Override
			public void registerObserver(@Nonnull Observer observer) {
				if (observer == null) throw new NullPointerException("null observer provided");

				if(observers.contains(observer)) throw new IllegalArgumentException("observer already registered");
				else observers.add(observer);
			}

			@Override
			public void unregisterObserver(@Nonnull Observer observer) {
				if(observer == null) throw new NullPointerException("provided observer is null");

				if(!observers.remove(observer)) throw new IllegalArgumentException("Observer provided was not registered");
				//the .remove() function returns a boolean value based on whether the removal was successful or not
				//if returns true, the observer was removed, and we don't need to do anything else
			}

			@Nonnull
			@Override
			public ImmutableSet<Observer> getObservers() {
				return ImmutableSet.copyOf(observers);
			}

			@Override
			public void chooseMove(@Nonnull Move move) {

				state = state.advance(move);
				//advances the game state

				for (Observer observer : observers) {
					if (state.getWinner().isEmpty()) observer.onModelChanged(state, Observer.Event.MOVE_MADE);
					//if there's no winner, then we update every observer to say only a move has been made
					else observer.onModelChanged(state, Observer.Event.GAME_OVER);
					//if there's a winner, then we update every observer to say there's a winner, and the game is over.
				}

			}
		};
	}
}
