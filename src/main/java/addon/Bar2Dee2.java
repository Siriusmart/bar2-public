package addon;

import com.mojang.logging.LogUtils;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import addon.commands.CommandExample;
import addon.modules.ClimbFly;
import addon.modules.FloppyFly;
import addon.modules.HighwayDigger;
import addon.modules.HighwayFly;
import addon.modules.Lander;
import addon.modules.LevelFly;
import addon.modules.Takeoff;

import org.slf4j.Logger;

public class Bar2Dee2 extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category BAR = new Category("Bar2");
    public static final Category CURRY = new Category("Curry2");
    public static final Category DEE = new Category("Dee2");
    // public static final HudGroup HUD_GROUP = new HudGroup("Bar2");

    @Override
    public void onInitialize() {
        LOG.info("Activating curry munchers.");

        Modules modules = Modules.get();

        // bar
        modules.add(new FloppyFly());
        modules.add(new HighwayFly());

        // curry
        modules.add(new LevelFly());
        modules.add(new ClimbFly());
        modules.add(new Lander());
        modules.add(new Takeoff());
        modules.add(new HighwayDigger());

        // Commands
        Commands.add(new CommandExample());

        // HUD
        // Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(BAR);
        Modules.registerCategory(CURRY);
    }

    @Override
    public String getPackage() {
        return "addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Siriusmart", "bar2dee2");
    }
}
