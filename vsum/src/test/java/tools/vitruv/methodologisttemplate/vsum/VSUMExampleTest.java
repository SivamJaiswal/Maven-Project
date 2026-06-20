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

import mir.reactions.familyPeople.FamilyPeopleChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

// people (source): Family contains Man and Woman
import tools.vitruv.methodologisttemplate.model.people.Family;
import tools.vitruv.methodologisttemplate.model.people.Man;
import tools.vitruv.methodologisttemplate.model.people.PeopleFactory;
import tools.vitruv.methodologisttemplate.model.people.Woman;

// family (target): Father and Mother are the corresponding objects
import tools.vitruv.methodologisttemplate.model.family.Father;
import tools.vitruv.methodologisttemplate.model.family.Mother;


public class VSUMExampleTest {

    // Register XMI format so EMF can read/write .model files on disk
    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("*", new XMIResourceFactoryImpl());
    }

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

        // After reload: Family still has 1 Man, Father still exists in family
        Assertions.assertEquals(1,
                getView(vsum, List.of(Family.class)).getRootObjects(Family.class)
                        .iterator().next().getMen().size());
        Assertions.assertEquals(1,
                getView(vsum, List.of(Father.class)).getRootObjects(Father.class).size());
    }

    // Insertion tests

    // When a Man is added to a Family, a Father with the same Name+Age must appear in family
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
                    return man.getName().equals(father.getName())
                            && man.getAge() == father.getAge();
                }));
    }

    // When a Woman is added to a Family, a Mother with the same Name+Age must appear in family
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

        modifyView(
                getView(vsum, List.of(Family.class)).withChangeDerivingTrait(), v -> {
                    v.getRootObjects(Family.class).iterator().next()
                            .getMen().get(0).setName(newName);
                });

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

        modifyView(
                getView(vsum, List.of(Family.class)).withChangeDerivingTrait(), v -> {
                    v.getRootObjects(Family.class).iterator().next()
                            .getWomen().get(0).setAge(99);
                });

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

    // When all Men are deleted from Family, all Fathers must be removed from family
    @Test
    void manDeleteRemovesFather(@TempDir Path tempDir) {
        VirtualModel vsum = createDefaultVirtualModel(tempDir);
        addFamily(vsum, tempDir);

        modifyView(
                getView(vsum, List.of(Family.class)).withChangeDerivingTrait(), v -> {
                    v.getRootObjects(Family.class).iterator().next()
                            .getMen().clear();
                });

        Assertions.assertTrue(assertView(
                getView(vsum, List.of(Father.class)), v ->
                        v.getRootObjects(Father.class).isEmpty()));
    }

    // When all Women are removed from Family, all Mothers must be removed from family
    @Test
    void womanDeleteRemovesMother(@TempDir Path tempDir) {
        VirtualModel vsum = createDefaultVirtualModel(tempDir);
        addFamily(vsum, tempDir);

        modifyView(
                getView(vsum, List.of(Family.class)).withChangeDerivingTrait(), v -> {
                    v.getRootObjects(Family.class).iterator().next()
                            .getWomen().clear();
                });

        Assertions.assertTrue(assertView(
                getView(vsum, List.of(Mother.class)), v ->
                        v.getRootObjects(Mother.class).isEmpty()));
    }

    // Helper methods

    private void addFamily(VirtualModel vsum, Path path) {
        modifyView(
                getView(vsum, List.of(Family.class)).withChangeDerivingTrait(), v -> {
                    Family family = PeopleFactory.eINSTANCE.createFamily();

                    Man man = PeopleFactory.eINSTANCE.createMan();
                    man.setName("John");
                    man.setAge(45);
                    family.getMen().add(man);

                    Woman woman = PeopleFactory.eINSTANCE.createWoman();
                    woman.setName("Anna");
                    woman.setAge(42);
                    family.getWomen().add(woman);

                    v.registerRoot(family,
                            URI.createFileURI(path.toString() + "/family.model"));
                });
    }

    private InternalVirtualModel createDefaultVirtualModel(Path projectPath) {
        InternalVirtualModel model = new VirtualModelBuilder()
                .withStorageFolder(projectPath)
                .withUserInteractorForResultProvider(
                        new TestUserInteraction.ResultProvider(new TestUserInteraction()))
                .withChangePropagationSpecifications(
                        new FamilyPeopleChangePropagationSpecification())
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

    private void modifyView(CommittableView view, Consumer<CommittableView> fn) {
        fn.accept(view);
        view.commitChanges();
    }

    private boolean assertView(View view, Function<View, Boolean> fn) {
        return fn.apply(view);
    }
}