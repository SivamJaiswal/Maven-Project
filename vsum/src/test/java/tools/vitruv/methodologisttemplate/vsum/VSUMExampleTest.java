package tools.vitruv.methodologisttemplate.vsum;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mir.reactions.model2Model2.Model2Model2ChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

// model(source) : Family contains Man and Woman
import tools.vitruv.methodologisttemplate.model.model.Family;
import tools.vitruv.methodologisttemplate.model.model.Man;
import tools.vitruv.methodologisttemplate.model.model.ModelFactory;
import tools.vitruv.methodologisttemplate.model.model.Woman;

// model2(target): Father and Mother are the corresponding objects
import tools.vitruv.methodologisttemplate.model.model2.Father;
import tools.vitruv.methodologisttemplate.model.model2.Mother;


public class VSUMExampleTest {

    // Register XMI format so EMF can read/write .model files on disk
    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
    }

    // Initialization ──────────────────────────────────────────────────

    // Verify the VSUM can be created, disposed, and restarted without errors
    @Test
    void reloadEmptyVirtualModel(@TempDir Path tempDir) {
        InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
        vsum.dispose();
        vsum = createDefaultVirtualModel(tempDir);
    }

    // Verify model state persists across VSUM restart 
    @Test
    void reloadFilledVirtualModel(@TempDir Path tempDir) {
        InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
        addFamily(vsum, tempDir);

        // Simulate application restart
        vsum.dispose();
        vsum = createDefaultVirtualModel(tempDir);

        // After reload: Family still has 1 Man, Father still exists in model2
        Assertions.assertEquals(1,
                getView(vsum, List.of(Family.class)).getRootObjects(Family.class)
                        .iterator().next().getMen().size());
        Assertions.assertEquals(1,
                getView(vsum, List.of(Father.class)).getRootObjects(Father.class).size());
    }

    // Insertion tests 

    // When a Man is added to a Family, a Father with the same Name+Age must appear in model2
    @Test
    void manInsertedCreatesFather(@TempDir Path tempDir) {
        VirtualModel vsum = createDefaultVirtualModel(tempDir);
        addFamily(vsum, tempDir);

        Assertions.assertTrue(assertView(
                getView(vsum, List.of(Family.class, Father.class)), v -> {
                    Man man = v.getRootObjects(Family.class)
                            .iterator().next().getMen().get(0);
                    Father father = v.getRootObjects(Father.class)
                            .iterator().next();
                    // Father must mirror Man's Name and Age
                    return man.getName().equals(father.getName())
                            && man.getAge() == father.getAge();
                }));
    }

    // When a Woman is added to a Family, a Mother with the same Name+Age must appear in model2
    @Test
    void womanInsertedCreatesMother(@TempDir Path tempDir) {
        VirtualModel vsum = createDefaultVirtualModel(tempDir);
        addFamily(vsum, tempDir);

        Assertions.assertTrue(assertView(
                getView(vsum, List.of(Family.class, Mother.class)), v -> {
                    Woman woman = v.getRootObjects(Family.class)
                            .iterator().next().getWomen().get(0);
                    Mother mother = v.getRootObjects(Mother.class)
                            .iterator().next();
                    // Mother must mirror Woman's Name and Age
                    return woman.getName().equals(mother.getName())
                            && woman.getAge() == mother.getAge();
                }));
    }

    // Update tests

    // When Man.Name changes, Father.name must be updated automatically
    @Test
    void manRenameUpdatesFather(@TempDir Path tempDir) {
        VirtualModel vsum = createDefaultVirtualModel(tempDir);
        addFamily(vsum, tempDir);

        final String newName = "UpdatedJohn";

        // Change Man's name on the model side 
        modifyView(
                getView(vsum, List.of(Family.class)).withChangeDerivingTrait(), v -> {
                    v.getRootObjects(Family.class).iterator().next()
                            .getMen().get(0).setName(newName);
                });

        // Both Man and Father must now have the updated name
        Assertions.assertTrue(assertView(
                getView(vsum, List.of(Family.class, Father.class)), v -> {
                    String manName = v.getRootObjects(Family.class)
                            .iterator().next().getMen().get(0).getName();
                    String fatherName = v.getRootObjects(Father.class)
                            .iterator().next().getName();
                    return manName.equals(newName) && fatherName.equals(newName);
                }));
    }

    // When Woman.Age changes, Mother.age must be updated automatically
    @Test
    void womanAgeUpdatesMother(@TempDir Path tempDir) {
        VirtualModel vsum = createDefaultVirtualModel(tempDir);
        addFamily(vsum, tempDir);

        // Change Woman's age on the model side
        modifyView(
                getView(vsum, List.of(Family.class)).withChangeDerivingTrait(), v -> {
                    v.getRootObjects(Family.class).iterator().next()
                            .getWomen().get(0).setAge(99);
                });

        // Both Woman and Mother must now have age 99
        Assertions.assertTrue(assertView(
                getView(vsum, List.of(Family.class, Mother.class)), v -> {
                    int womanAge = v.getRootObjects(Family.class)
                            .iterator().next().getWomen().get(0).getAge();
                    int motherAge = v.getRootObjects(Mother.class)
                            .iterator().next().getAge();
                    return womanAge == motherAge;
                }));
    }

    // Delete tests 

    // When all Men are deleted from Family, all Fathers must be removed from model2
    @Test
    void manDeleteRemovesFather(@TempDir Path tempDir) {
        VirtualModel vsum = createDefaultVirtualModel(tempDir);
        addFamily(vsum, tempDir);

        // Clear the men list 
        modifyView(
                getView(vsum, List.of(Family.class)).withChangeDerivingTrait(), v -> {
                    v.getRootObjects(Family.class).iterator().next()
                            .getMen().clear();
                });

        // model2 must now have no Father objects
        Assertions.assertTrue(assertView(
                getView(vsum, List.of(Father.class)), v ->
                        v.getRootObjects(Father.class).isEmpty()));
    }

    // When all Women are removed from Family, all Mothers must be removed from model2
    @Test
    void womanDeleteRemovesMother(@TempDir Path tempDir) {
        VirtualModel vsum = createDefaultVirtualModel(tempDir);
        addFamily(vsum, tempDir);

        // Clear the women list 
        modifyView(
                getView(vsum, List.of(Family.class)).withChangeDerivingTrait(), v -> {
                    v.getRootObjects(Family.class).iterator().next()
                            .getWomen().clear();
                });

        // model2 must now have no Mother objects
        Assertions.assertTrue(assertView(
                getView(vsum, List.of(Mother.class)), v ->
                        v.getRootObjects(Mother.class).isEmpty()));
    }

    // Helper methods

    
    private void addFamily(VirtualModel vsum, Path path) {
        modifyView(
                getView(vsum, List.of(Family.class)).withChangeDerivingTrait(), v -> {
                    Family family = ModelFactory.eINSTANCE.createFamily();

                    Man man = ModelFactory.eINSTANCE.createMan();
                    man.setName("John");
                    man.setAge(45);
                    family.getMen().add(man);

                    Woman woman = ModelFactory.eINSTANCE.createWoman();
                    woman.setName("Anna");
                    woman.setAge(42);
                    family.getWomen().add(woman);

                    // Register as a root resource — gives the Family a URI on disk
                    v.registerRoot(family,
                            URI.createFileURI(path.toString() + "/family.model"));
                });
    }

     //Builds a fresh VSUM 
     //TRANSITIVE_CYCLIC mode so reactions can trigger further reactions
    
    private InternalVirtualModel createDefaultVirtualModel(Path projectPath) {
        InternalVirtualModel model = new VirtualModelBuilder()
                .withStorageFolder(projectPath)
                .withUserInteractorForResultProvider(
                        new TestUserInteraction.ResultProvider(new TestUserInteraction()))
                .withChangePropagationSpecifications(
                        new Model2Model2ChangePropagationSpecification())
                .buildAndInitialize();
        model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
        return model;
    }

    private View getView(VirtualModel vsum, Collection<Class<?>> rootTypes) {
        var selector = vsum.createSelector(
                ViewTypeFactory.createIdentityMappingViewType("familyView"));
        selector.getSelectableElements().stream()
                .filter(e -> rootTypes.stream().anyMatch(t -> t.isInstance(e)))
                .forEach(e -> selector.setSelected(e, true));
        return selector.createView();
    }

    //Applies changes to a view and commits them to the VSUM.
    private void modifyView(CommittableView view, Consumer<CommittableView> fn) {
        fn.accept(view);
        view.commitChanges(); // ← reactions fire here
    }

    // Runs an assertion lambda against a view and returns the boolean result
    private boolean assertView(View view, Function<View, Boolean> fn) {
        return fn.apply(view);
    }
}