package beamline.miners.trieconformance;


import java.util.HashMap;

//

public class StatesBuffer {

    protected HashMap<String, State> currentStates;

    public StatesBuffer (String algString, State state){

        currentStates.put(algString, state);

    }

    public StatesBuffer (HashMap currentStates){

        this.currentStates = currentStates;

    }

    public void setCurrentStates(HashMap<String, State> currentStates){
        this.currentStates = currentStates;
    }

    public HashMap<String, State> getCurrentStates() {
        return currentStates;
    }

}
