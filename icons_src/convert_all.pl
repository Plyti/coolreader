#!/usr/bin/perl -w

$TARGET_DIR = "../android/res/";

my %ic_actions_sizes  = (ldpi=>24, mdpi=>32, hdpi=>48, xhdpi=>64, xxhdpi=>96,  xxxhdpi=>128);
my %ic_menu_sizes     = (ldpi=>36, mdpi=>48, hdpi=>72, xhdpi=>96, xxhdpi=>144, xxxhdpi=>192);
my %ic_launcher_sizes = (ldpi=>36, mdpi=>48, hdpi=>72, xhdpi=>96, xxhdpi=>144, xxxhdpi=>192);

my %ic_actions_list=(
#	'cr3_button_prev_hc-256x256-src.svg' => 'cr3_button_prev_hc.png'
);

my %ic_menu_list=(
#	'ic_menu_trigmode_dark-src.svg' => 'ic_menu_trigmode_dark.png',
);

my %ic_launcher_list=(
	'cr3_logo_hc-256x256-src.svg' => 'cr3_logo_hc.png'
);

my ($srcfile, $dstfile);
my ($dpi, $size);
my $folder;
my $resfile;
my $cmd;
my $ret;
while (($srcfile, $dstfile) = each(%ic_actions_list))
{
	while (($dpi, $size) = each(%ic_actions_sizes))
	{
		$folder = "${TARGET_DIR}/drawable-${dpi}/";
		if (-d $folder)
		{
			$resfile = "${TARGET_DIR}/drawable-${dpi}/${dstfile}";
			$cmd = "inkscape -z -e ${resfile} -w ${size} -h ${size} ${srcfile}";
			print "$cmd\n";
			$ret = system($cmd);
			print "Failed!\n" if $ret != 0;
		}
	}
}

while (($srcfile, $dstfile) = each(%ic_menu_list))
{
	while (($dpi, $size) = each(%ic_menu_sizes))
	{
		$folder = "${TARGET_DIR}/drawable-${dpi}/";
		if (-d $folder)
		{
			$resfile = "${TARGET_DIR}/drawable-${dpi}/${dstfile}";
			$cmd = "inkscape -z -e ${resfile} -w ${size} -h ${size} ${srcfile}";
			print "$cmd\n";
			$ret = system($cmd);
			print "Failed!\n" if $ret != 0;
		}
	}
}

while (($srcfile, $dstfile) = each(%ic_launcher_list))
{
	while (($dpi, $size) = each(%ic_launcher_sizes))
	{
		$folder = "${TARGET_DIR}/drawable-${dpi}/";
		if (-d $folder)
		{
			$resfile = "${TARGET_DIR}/drawable-${dpi}/${dstfile}";
			$cmd = "inkscape -z -e ${resfile} -w ${size} -h ${size} ${srcfile}";
			print "$cmd\n";
			$ret = system($cmd);
			print "Failed!\n" if $ret != 0;
		}
	}
}
