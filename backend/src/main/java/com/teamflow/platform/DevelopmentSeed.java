package com.teamflow.platform;

import com.teamflow.identity.IdentityService;
import com.teamflow.identity.UserRepository;
import com.teamflow.workspace.WorkspaceService;
import com.teamflow.work.WorkService;
import com.teamflow.platform.Api.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/** Opt-in development fixtures. No password is supplied by the application. */
@Component
@Profile("dev")
public class DevelopmentSeed implements ApplicationRunner {
  private final IdentityService identity;
  private final UserRepository users;
  private final WorkspaceService spaces;
  private final WorkService work;
  private final String password;
  public DevelopmentSeed(IdentityService identity,UserRepository users,WorkspaceService spaces,WorkService work,@Value("${DEV_SEED_PASSWORD:}") String password){this.identity=identity;this.users=users;this.spaces=spaces;this.work=work;this.password=password;}
  public void run(ApplicationArguments args){
    if(password.isEmpty())return;
    if(password.length()<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("DEV_SEED_PASSWORD must be 12 to 72 UTF-8 bytes.");
    String owner="alex@teamflow.example",member="morgan@teamflow.example";
    if(users.findByEmail(owner).isPresent())return;
    identity.register(owner,"Alex Rivers",password);
    if(users.findByEmail(member).isEmpty())identity.register(member,"Morgan Lee",password);
    var workspace=spaces.create(owner,new WorkspaceInput("Product Studio"));
    spaces.setMember(owner,new MemberInput(workspace.id(),member,Role.MEMBER));
    var project=work.createProject(owner,new ProjectInput(workspace.id(),"Launch readiness"));
    work.createTask(owner,new TaskInput(project.id(),"Review release checklist","Check database migration, rollback and the customer journey.",TaskStatus.IN_PROGRESS,Priority.HIGH,identity.me(member).id(),null));
    work.createTask(owner,new TaskInput(project.id(),"Plan the next iteration","Capture feedback from the team.",TaskStatus.TODO,Priority.MEDIUM,null,null));
  }
}
