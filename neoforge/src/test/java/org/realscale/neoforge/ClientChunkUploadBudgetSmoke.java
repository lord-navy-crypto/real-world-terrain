package org.realscale.neoforge;

/** Pure Java executable regression checks, no Minecraft runtime required. */
public final class ClientChunkUploadBudgetSmoke {
    private static void yes(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ClientChunkUploadBudget budget=new ClientChunkUploadBudget(2_000_000,8);
        yes(!budget.canTakeTask(),"Outside render frame must be vanilla");
        budget.beginFrame(false);
        yes(!budget.canTakeTask(),"Loading/menu must remain vanilla");
        budget.beginFrame(true);
        yes(budget.canTakeTask(),"Playable frame starts with capacity");
        for(int i=0;i<7;i++){
            yes(budget.canTakeTask(),"Budget has room for "+i);
            budget.completed(100_000);
        }
        yes(budget.canTakeTask(),"The eighth task is admitted");
        budget.completed(100_000);
        yes(!budget.canTakeTask(),"Eight-task hard cap");
        budget.recordDeferred();
        budget.beginFrame(true);
        yes(budget.tasksThisFrame()==0,"New frame resets per-frame quota");
        budget.completed(2_500_000);
        yes(!budget.canTakeTask(),"Expensive upload exhausts time allowance");
        budget.recordDeferred();
        yes(budget.deferredCalls()==2L,"Deferred calls counted, not dropped");
        budget.endFrame();
        yes(!budget.canTakeTask(),"Post closes frame");
        yes(budget.totalTasks()==9,"All completed Runnable counts retained");
        boolean rejected=false;
        try{new ClientChunkUploadBudget(0,1);}catch(IllegalArgumentException x){
            rejected=true;
        }
        yes(rejected,"Invalid budget rejected");
        System.out.println("REALSCALE_CLIENT_UPLOAD_BUDGET_SMOKE_PASS");
    }
}
