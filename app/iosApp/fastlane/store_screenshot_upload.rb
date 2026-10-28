require "deliver"
require "fileutils"

module StoreScreenshotUpload
  DUO_SCENES = %w[01_home 03_report_graph 04_blunder_list 05_drill_question 06_drill_result].freeze
  DISPLAY_TYPE = Spaceship::ConnectAPI::AppScreenshotSet::DisplayType

  def self.plan(project_dir: File.expand_path("..", __dir__))
    normal_root = File.join(project_dir, "fastlane/screenshots")
    normal = Dir.glob(File.join(normal_root, "*", "*.png")).sort.map do |path|
      image = Deliver::AppScreenshot.new(path, File.basename(File.dirname(path)))
      raise "Unsupported standard screenshot: #{path}" if image.display_type.nil? || image.display_type == DISPLAY_TYPE::APP_IPHONE_DUO
      image
    end
    raise "No standard screenshots found" if normal.empty?

    duo = normal.map(&:language).uniq.flat_map do |language|
      DUO_SCENES.flat_map do |scene|
        %w[inner outer].map do |display|
          path = File.join(project_dir, "fastlane/duo_screenshots", language, "#{scene}_duo_#{display}.png")
          raise "Missing Duo screenshot: #{path}" unless File.file?(path)
          image = Deliver::AppScreenshot.new(path, language)
          raise "Unexpected Duo screenshot dimensions: #{path}" unless image.display_type == DISPLAY_TYPE::APP_IPHONE_DUO
          image
        end
      end
    end
    images = normal + duo
    # 内外は同じ表示区分に入るため、合計でも上限10枚を超えないようにする。
    images.group_by { |image| [image.language, image.display_type] }.each do |group, screenshots|
      raise "More than 10 screenshots for #{group.join('/')}" if screenshots.length > 10
    end
    images
  end

  def self.prepare(project_dir: File.expand_path("..", __dir__))
    images = plan(project_dir: project_dir)
    output = File.join(project_dir, "build/store_screenshots")
    FileUtils.rm_rf(output)
    images.each do |image|
      directory = File.join(output, image.language)
      FileUtils.mkdir_p(directory)
      FileUtils.cp(image.path, File.join(directory, File.basename(image.path)))
    end
    output
  end
end

puts StoreScreenshotUpload.prepare if $PROGRAM_NAME == __FILE__
