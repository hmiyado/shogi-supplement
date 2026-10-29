require "minitest/autorun"
require "tmpdir"
require "digest"
require_relative "../store_screenshot_upload"

class StoreScreenshotUploadTest < Minitest::Test
  def setup
    @project_dir = Dir.mktmpdir("store-screenshot-upload")
    source = File.expand_path("../..", __dir__)
    %w[screenshots duo_screenshots].each do |name|
      target = File.join(@project_dir, "fastlane", name)
      FileUtils.mkdir_p(File.dirname(target))
      FileUtils.cp_r(File.join(source, "fastlane", name), target)
    end
  end

  def teardown
    FileUtils.remove_entry(@project_dir)
  end

  def test_prepares_supported_groups_and_copies_generated_pixels_verbatim
    plan = StoreScreenshotUpload.plan(project_dir: @project_dir)
    counts = plan.group_by(&:display_type).transform_values(&:length)
    assert_equal({ "APP_IPHONE_67" => 6, "APP_IPHONE_65" => 6, "APP_IPHONE_DUO" => 10 }, counts)
    output = StoreScreenshotUpload.prepare(project_dir: @project_dir)
    assert_equal 22, Dir.glob(File.join(output, "ja/*.png")).length
    plan.each do |image|
      copied = File.join(output, image.language, File.basename(image.path))
      assert_equal Digest::SHA256.file(image.path).hexdigest, Digest::SHA256.file(copied).hexdigest
    end
    assert_equal 12, Dir.glob(File.join(@project_dir, "fastlane/duo_screenshots/ja/*.png")).length
    refute Dir.glob(File.join(output, "ja/*dark*duo*.png")).any?
  end

  def test_rejects_missing_selected_image_before_replacing_prepared_output
    sentinel = existing_output
    FileUtils.rm(File.join(@project_dir, "fastlane/duo_screenshots/ja/01_home_duo_inner.png"))
    assert_raises(RuntimeError) { StoreScreenshotUpload.prepare(project_dir: @project_dir) }
    assert_equal "prepared", File.read(sentinel)
  end

  def test_rejects_iphone_image_substituted_for_duo
    sentinel = existing_output
    normal = Dir.glob(File.join(@project_dir, "fastlane/screenshots/ja/*.png")).first
    FileUtils.cp(normal, File.join(@project_dir, "fastlane/duo_screenshots/ja/01_home_duo_inner.png"))
    assert_raises(RuntimeError) { StoreScreenshotUpload.prepare(project_dir: @project_dir) }
    assert_equal "prepared", File.read(sentinel)
  end

  def test_rejects_more_than_ten_images_in_a_display_group
    source = Dir.glob(File.join(@project_dir, "fastlane/screenshots/ja/*_6.5.png")).first
    5.times do |index|
      FileUtils.cp(source, File.join(@project_dir, "fastlane/screenshots/ja/extra_#{index}_6.5.png"))
    end
    assert_raises(RuntimeError) { StoreScreenshotUpload.plan(project_dir: @project_dir) }
  end

  private

  def existing_output
    directory = File.join(@project_dir, "build/store_screenshots")
    FileUtils.mkdir_p(directory)
    path = File.join(directory, "previous.txt")
    File.write(path, "prepared")
    path
  end
end
